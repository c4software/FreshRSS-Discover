package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkQueue
import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.di.IoDispatcher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadTransmissionScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nombre d'articles transmis par requête `edit-tag`.
 *
 * Deux bornes, et la valeur est choisie entre elles :
 *
 * - **par le bas**, une page du flux fait 40 articles : un lot plus petit
 *   ferait plusieurs requêtes pour une seule page parcourue, ce que le
 *   traitement par lots existe précisément pour éviter (SPECS.md §4.5) ;
 * - **par le haut**, chaque article est un champ `i` du formulaire, et PHP
 *   n'accepte par défaut que 1 000 champs par requête (`max_input_vars`).
 *   Au-delà, les champs excédentaires sont **silencieusement ignorés** — et
 *   `edit-tag` répond `OK` sans rendre aucun compte par article
 *   (docs/freshrss-api.md §4.1). La perte serait donc totalement muette.
 *
 * 100 laisse un ordre de grandeur sous cette limite, couvre plus de deux pages
 * de lecture d'une seule requête, et pèse environ 2 ko de corps.
 */
private const val BATCH_SIZE = 100

private const val HTTP_UNAUTHORIZED = 401

/**
 * Marquage optimiste, et transmission par lots de ce qui attend.
 *
 * Le partage des rôles est celui de SPECS.md §4.5 : `ArticleCache` porte l'état
 * lu **local**, `PendingMarkQueue` porte ce qui reste à dire au serveur. Les
 * deux écritures se font ensemble à la lecture d'un article, et rien d'autre ne
 * les relie — c'est ce qui permet à la transmission d'échouer sans que la
 * lecture s'en aperçoive.
 */
@Singleton
internal class DefaultReadSyncRepository @Inject constructor(
    private val api: FreshRssApi,
    private val sessionStore: SessionStore,
    private val articleCache: ArticleCache,
    private val queue: PendingMarkQueue,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope applicationScope: CoroutineScope,
) : ReadSyncRepository {
    /**
     * Regroupe les transmissions dans le temps (SPECS.md §4.5).
     *
     * La portée est l'applicative, et non celle de l'appelant : une fenêtre
     * ouverte pendant la lecture doit survivre à la disparition de l'écran qui
     * l'a ouverte, sans quoi le marquage attendrait le lancement suivant pour
     * partir.
     *
     * La file est consultée **avant** la session : sans rien à transmettre, il
     * n'y a ni requête à faire ni session à exiger. C'est le cas courant du
     * démarrage, où `flush()` est appelée sans savoir s'il reste quelque chose.
     */
    private val scheduler = ReadTransmissionScheduler(scope = applicationScope) {
        withContext(ioDispatcher) {
            val session = sessionStore.observeSession().first()
            if (queue.pending(limit = 1).isNotEmpty() && session != null) transmit(session)
        }
    }

    /**
     * Bascule l'état local, met en file, **puis** ouvre la fenêtre de
     * regroupement. L'ordre compte deux fois :
     *
     * - l'état local d'abord, parce que c'est ce que l'utilisateur voit et que
     *   la file n'est qu'un moyen. Si le processus était tué entre les deux,
     *   l'article resterait lu à l'écran — une conséquence sans gravité — là où
     *   l'ordre inverse laisserait un marquage en file pour un article affiché
     *   comme non lu ;
     * - la fenêtre en dernier, parce que c'est cet ordre qui garantit qu'une
     *   fenêtre déjà ouverte emportera bien ce qui vient d'être mis en file.
     *
     * Rien ici n'attend le réseau : la fenêtre s'ouvre et cet appel rend la
     * main.
     */
    override suspend fun markAsRead(ids: Set<ArticleId>) = withContext(ioDispatcher) {
        if (ids.isNotEmpty()) {
            val ordered = ids.toList()
            articleCache.markAsRead(ordered)
            queue.enqueue(ordered)
            scheduler.schedule()
        }
    }

    /**
     * Force la transmission, sans attendre la fenêtre en cours.
     *
     * C'est le sens que `flush()` avait déjà — le rejeu au démarrage — et le
     * regroupement ne le change pas : ce qui est différé, c'est le marquage
     * ordinaire, pas la demande explicite d'envoyer.
     */
    override suspend fun flush() = scheduler.transmitNow()

    /**
     * La fenêtre en cours est abandonnée avec la file : à la déconnexion, elle
     * n'aurait plus rien à dire au serveur.
     */
    override suspend fun clearPending() = withContext(ioDispatcher) {
        scheduler.cancelScheduled()
        queue.clear()
    }

    /**
     * Vide la file lot par lot, en acquittant chaque lot confirmé.
     *
     * La file est relue à chaque tour plutôt que découpée d'avance : un
     * marquage peut s'y ajouter pendant la transmission, et l'acquittement
     * partiel doit rester la seule chose qui en retire des lignes.
     *
     * Le jeton de modification obtenu sert à tous les lots suivants — le
     * redemander à chaque requête doublerait le nombre d'allers-retours pour
     * rien, puisqu'il est déterministe et réutilisable (docs/freshrss-api.md
     * §2.3).
     */
    private suspend fun transmit(session: AuthSession) {
        var token = session.modificationToken
        var done = false

        while (!done) {
            val batch = queue.pending(BATCH_SIZE)
            if (batch.isEmpty()) {
                done = true
                continue
            }
            when (val outcome = sendBatch(session, batch, token)) {
                is BatchOutcome.Sent -> {
                    // L'acquittement suit la confirmation, jamais l'envoi : une
                    // ligne retirée trop tôt serait un article perdu.
                    queue.acknowledge(batch)
                    token = outcome.token
                }

                /*
                 * Seuls les jetons tombent, et l'aiguillage racine ramène de
                 * lui-même à l'écran de connexion, prérempli (SPECS.md §3.4).
                 * La file, elle, est intacte : ces marquages doivent survivre à
                 * la reconnexion, sinon l'utilisateur reverrait comme non lu ce
                 * qu'il a lu avant l'expiration.
                 */
                BatchOutcome.SessionLost -> {
                    sessionStore.invalidateTokens()
                    done = true
                }

                // Rien n'est parti, rien n'est perdu : la file conserve, et le
                // prochain passage retentera (SPECS.md §4.5).
                BatchOutcome.Deferred -> done = true
            }
        }
    }

    /**
     * Envoie un lot avec le jeton connu, et n'en redemande un qu'en cas de `401`.
     *
     * Le jeton connu peut venir d'un lancement précédent : il est enregistré
     * avec la session. Le supposer valide est le bon pari — il est déterministe
     * — et le `401` est le seul signal fiable de son invalidation.
     */
    private suspend fun sendBatch(
        session: AuthSession,
        batch: List<ArticleId>,
        knownToken: ModificationToken?,
    ): BatchOutcome {
        val token = knownToken ?: return renewThenSend(session, batch)
        return when (val sent = api.markAsRead(session.server, session.token, token, batch)) {
            is ApiOutcome.Success -> BatchOutcome.Sent(token)
            is ApiOutcome.HttpError ->
                if (sent.status == HTTP_UNAUTHORIZED) renewThenSend(session, batch) else BatchOutcome.Deferred

            else -> BatchOutcome.Deferred
        }
    }

    /**
     * Redemande le jeton de modification, puis retente — **une seule fois**.
     *
     * C'est la conduite prescrite par docs/freshrss-api.md §2.3. Boucler
     * au-delà n'apporterait rien : un jeton fraîchement obtenu et déjà refusé
     * ne désigne plus le jeton mais la session elle-même.
     */
    private suspend fun renewThenSend(session: AuthSession, batch: List<ArticleId>): BatchOutcome =
        when (val renewed = requestModificationToken(session)) {
            is ApiOutcome.Success -> sendWithRenewedToken(session, batch, renewed.value)
            is ApiOutcome.HttpError -> unauthorizedOrDeferred(renewed.status)
            else -> BatchOutcome.Deferred
        }

    /** Dernière tentative : un `401` ici n'est plus imputable au jeton. */
    private suspend fun sendWithRenewedToken(
        session: AuthSession,
        batch: List<ArticleId>,
        token: ModificationToken,
    ): BatchOutcome =
        when (val sent = api.markAsRead(session.server, session.token, token, batch)) {
            is ApiOutcome.Success -> BatchOutcome.Sent(token)
            is ApiOutcome.HttpError -> unauthorizedOrDeferred(sent.status)
            else -> BatchOutcome.Deferred
        }

    /**
     * Le jeton obtenu est enregistré avec la session : il survit ainsi au
     * redémarrage, et le rejeu au lancement suivant part directement sur
     * `edit-tag` (docs/freshrss-api.md §2.3).
     */
    private suspend fun requestModificationToken(session: AuthSession): ApiOutcome<ModificationToken> {
        val outcome = api.modificationToken(session.server, session.token)
        if (outcome is ApiOutcome.Success) {
            sessionStore.save(session.copy(modificationToken = outcome.value))
        }
        return outcome
    }

    /**
     * Un statut autre que `401` est un incident du serveur, pas un refus : il
     * relève du report, comme une coupure réseau. Rien n'est retiré de la file.
     */
    private fun unauthorizedOrDeferred(status: Int): BatchOutcome =
        if (status == HTTP_UNAUTHORIZED) BatchOutcome.SessionLost else BatchOutcome.Deferred
}

/**
 * Sort d'un lot, à l'usage interne de la boucle de transmission.
 *
 * Un sort par **requête**, pas par file entière : conclure la boucle de
 * transmission sur le sort du premier lot ferait passer un envoi partiel pour
 * une synchronisation complète.
 */
private sealed interface BatchOutcome {
    /** Confirmé par le serveur. [token] est celui qui a fonctionné : les lots suivants le réutilisent. */
    data class Sent(val token: ModificationToken) : BatchOutcome

    /** Le serveur refuse la session, jeton renouvelé compris. */
    data object SessionLost : BatchOutcome

    /** Rien n'est parti, rien n'est perdu : ce lot repartira plus tard. */
    data object Deferred : BatchOutcome
}
