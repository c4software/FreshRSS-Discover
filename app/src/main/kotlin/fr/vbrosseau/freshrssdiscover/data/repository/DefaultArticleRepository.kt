package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.HTTP_UNAUTHORIZED
import fr.vbrosseau.freshrssdiscover.data.api.StreamContentsDto
import fr.vbrosseau.freshrssdiscover.data.api.toArticlePage
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.di.IoDispatcher
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedResult
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.shuffle.interleaveBySource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nombre d'articles demandés par page.
 *
 * Tranche SPECS.md §8 question 1. Mesuré sur un flux réel : résumé médian de
 * 1 324 caractères, 90ᵉ centile à 4 379 — une page de 40 pèse donc environ
 * 55 ko. Assez d'avance pour que le défilement ne s'interrompe pas, sans
 * retarder le premier affichage.
 */
private const val PAGE_SIZE = 40

@Singleton
internal class DefaultArticleRepository @Inject constructor(
    private val api: FreshRssApi,
    private val sessionStore: SessionStore,
    private val cache: ArticleCache,
    private val freshness: FeedFreshnessRepository,
    private val network: NetworkAvailability,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArticleRepository {
    override suspend fun loadPage(
        cursor: PageCursor?,
        previousTail: List<Article>,
    ): FeedResult<ArticlePage> = withContext(ioDispatcher) {
        // Rien ne précède le début du flux : un rechargement complet doit
        // produire exactement le même ordre que le premier affichage (règle 3).
        val tail = if (cursor == null) emptyList() else previousTail
        fetchPage(cursor, previousTail = tail, renewsCache = false)
    }

    /**
     * Le mélange repart de zéro : la page rendue est la tête du flux, rien ne
     * la précède.
     *
     * **Et le cache est renouvelé**, ce qu'il n'était pas (GOAL-026) : le
     * rechargement vidait l'affichage sans toucher à la base, si bien qu'une
     * application tuée puis relancée ressuscitait le flux qu'on venait
     * d'épuiser. Voir [renewCache].
     */
    override suspend fun refresh(): FeedResult<ArticlePage> = withContext(ioDispatcher) {
        fetchPage(cursor = null, previousTail = emptyList(), renewsCache = true)
    }

    /**
     * Les articles lus sont écartés ici, faute de pouvoir l'être par la requête.
     *
     * Le cache les conserve jusqu'à la purge (SPECS.md §5.4) alors que le flux
     * ne présente que des non-lus (§4.1). La borne s'applique donc avant le
     * filtrage : un cache chargé d'articles lus rend une liste plus courte que
     * demandée — sans conséquence, la première page réseau la complète aussitôt.
     */
    override suspend fun unreadFromCache(limit: Int): List<Article> =
        // Mélangés comme le flux (SPECS.md §4.2) : le rappel cite les premiers
        // titres, et les prendre dans l'ordre brut de la base ferait toujours
        // citer la source la plus bavarde.
        interleaveBySource(cache.unreadArticles(limit))

    /**
     * Le cache mélangé (SPECS.md §4.2), **articles lus compris**.
     *
     * C'est ce qui rend le lancement stable : l'ensemble ne bouge pas entre
     * deux ouvertures, donc le mélange rend le même ordre. Écarter les lus
     * faisait changer cet ensemble à chaque session — le marquage en consomme —
     * et le flux paraissait se remélanger tout seul, sans qu'aucune requête ne
     * soit partie. Les lus restent affichés, grisés à leur place, jusqu'au
     * prochain rechargement demandé (SPECS.md §4.6), qui seul renouvelle la
     * liste.
     *
     * Le rappel de lecture, lui, ne veut que les non-lus : c'est
     * [unreadFromCache], et son filtre est fait par SQLite.
     */
    override fun observeCachedArticles(limit: Int): Flow<List<Article>> =
        cache.observeArticles(limit).map(::interleaveBySource)

    private suspend fun fetchPage(
        cursor: PageCursor?,
        previousTail: List<Article>,
        renewsCache: Boolean,
    ): FeedResult<ArticlePage> {
        /*
         * Aucune session : l'aiguillage racine a déjà dû basculer vers l'écran
         * de connexion. Le signaler quand même plutôt que de renvoyer une page
         * vide, qui se lirait comme « plus d'articles ».
         */
        val session = sessionStore.observeSession().first() ?: return Outcome.Failure(FeedError.SessionExpired)

        return api.streamContents(
            address = session.server,
            token = session.token,
            pageSize = PAGE_SIZE,
            cursor = cursor,
        ).toFeedResult(previousTail, renewsCache)
    }

    /**
     * Le rechargement renouvelle le cache : **la réponse du serveur devient son
     * contenu** (SPECS.md §4.6, GOAL-027).
     *
     * **Le défaut, signalé deux fois par l'auteur.** Le rechargement vidait
     * l'affichage et laissait la base intacte : tout lire, recharger — l'écran
     * annonçait qu'il n'y avait plus rien — puis tuer l'application et la
     * relancer ressuscitait le jeu précédent. Depuis GOAL-020 il n'y a plus de
     * fanion de lecture : ces revenants étaient **indiscernables** de vrais non
     * lus.
     *
     * GOAL-026 avait purgé ce qui était **lu localement**, et ne suffisait pas.
     * Mesuré sur l'appareil de l'auteur, base à l'appui : après un rechargement
     * qui affichait « rien à lire », le cache gardait 31 lignes **non lues
     * localement** que le serveur ne renvoyait plus. Il les avait lues ailleurs
     * — interface web, autre client — et `upsertPreservingLocalReadState` ne
     * propage l'état lu que pour les articles que le serveur **renvoie** :
     * l'absence, elle, ne disait rien. C'est pourtant le seul signe que
     * l'application reçoive jamais d'une lecture faite ailleurs.
     *
     * Le critère est donc l'appartenance à la page rendue, jamais l'état lu
     * local. Sont épargnées les lignes dont le marquage **attend encore de
     * partir** : elles portent une vérité que le serveur ignore, il ne peut donc
     * pas la renvoyer, et les effacer ferait revenir comme neuf ce que
     * l'utilisateur vient de lire.
     *
     * **Après l'enregistrement, jamais avant** : `upsertPreservingLocalReadState`
     * lit l'état lu dans ces mêmes lignes.
     *
     * **La pagination ne renouvelle rien** : ce serait effacer le flux sous les
     * yeux de qui le parcourt, puisqu'une page suivante ne contient jamais ce
     * qui précède. Seul un rechargement demandé renouvelle, comme SPECS.md §4.6
     * le dit — et le prix, assumé, est que la réserve hors ligne retombe alors à
     * la page de tête.
     */
    private suspend fun renewCache(returned: List<Article>) {
        cache.retainOnly(returned.map(Article::id))
    }

    /**
     * Ramène la page à l'ordre de publication, puis applique le mélange
     * (SPECS.md §4.2).
     *
     * **Le tri n'est pas une précaution, c'est le correctif d'un défaut vu à
     * l'écran.** Le serveur trie sa liste par date de récupération, pas de
     * publication : constaté sur une instance réelle, un article publié deux
     * jours plus tôt ouvrait la première page. Affichée telle quelle, cette
     * page installait un ordre différent de celui du cache — trié par
     * publication — et l'écran du lancement dépendait alors de qui, du disque
     * ou du réseau, répondait en premier : chaque démarrage tirait son ordre
     * au sort. La reprise de lecture (SPECS.md §5.3), qui cherche « le premier
     * article pas plus récent », tombait de surcroît n'importe où dans une
     * liste non chronologique.
     *
     * Le départage à date égale est celui du tri SQL du cache — id décroissant —
     * pour que les deux sources produisent exactement le même ordre. Le mélange
     * attend d'ailleurs du chronologique inverse : c'est son contrat, que
     * l'ordre de récupération violait.
     *
     * Le cache, lui, reçoit l'ordre du serveur : il est relu trié par date, et
     * le mélange y est réappliqué à la lecture. Persister un ordre déjà mélangé
     * n'apporterait rien et le figerait alors que de nouveaux articles doivent
     * pouvoir s'y insérer.
     */
    private fun ArticlePage.interleaved(previousTail: List<Article>): ArticlePage {
        val chronological = articles.sortedWith(
            compareByDescending<Article> { it.publishedAtEpochSeconds }
                .thenByDescending { it.id.value },
        )
        return copy(articles = interleaveBySource(chronological, previousTail))
    }

    /**
     * Chaque page obtenue est déposée au cache **avant** d'être rendue.
     *
     * C'est ce qui permettra au flux de s'afficher immédiatement au prochain
     * lancement, sans attendre le réseau (SPECS.md §5.1). L'écriture est faite
     * ici plutôt que par l'appelant : un appelant qui l'oublierait produirait un
     * cache incomplet, et le défaut ne se verrait qu'au lancement suivant.
     *
     * **La date du contact serveur est notée au même endroit, et pour la même
     * raison.** C'est elle qui dira plus tard si le flux affiché est ancien
     * (SPECS.md §4.6). Deux ViewModels demandent des pages ; la noter chez eux
     * ferait vivre deux fois la même règle, et laisserait les deux modes de
     * présentation diverger. La couche qui a parlé au serveur est la seule à
     * savoir qu'il a répondu.
     *
     * Une page **valide mais vide** compte comme une réponse : le serveur a
     * parlé, c'est le flux qui n'a rien de neuf. Un échec, lui, ne note rien —
     * sans quoi une application ouverte hors ligne toute la journée
     * paraîtrait fraîche.
     */
    private suspend fun ApiOutcome<StreamContentsDto>.toFeedResult(
        previousTail: List<Article>,
        renewsCache: Boolean,
    ): FeedResult<ArticlePage> = when (this) {
        is ApiOutcome.Success -> {
            val page = value.toArticlePage()
            cache.save(page.articles)
            if (renewsCache) renewCache(page.articles)
            freshness.recordRefresh()
            Outcome.Success(page.interleaved(previousTail))
        }

        is ApiOutcome.HttpError -> httpFailure(status)

        is ApiOutcome.MalformedResponse -> Outcome.Failure(FeedError.Unexpected(detail))

        /*
         * La connectivité n'est lue qu'ici, au moment de l'échec : la constater
         * d'avance donnerait une réponse périmée, le réseau pouvant disparaître
         * pendant la requête — ce qui est exactement le cas à diagnostiquer.
         */
        is ApiOutcome.TransportError -> Outcome.Failure(
            if (network.isOnline()) FeedError.ServerUnreachable else FeedError.NoNetwork,
        )
    }

    /**
     * Un `401` sur une lecture signifie que le serveur refuse le jeton —
     * l'utilisateur a changé son mot de passe API.
     *
     * La session est effacée **ici**, ce qui fait basculer l'aiguillage racine
     * de lui-même : aucun écran n'a de redirection à déclencher (SPECS.md
     * §3.4). Le rappel de saisie, lui, survit : adresse et identifiant restent
     * préremplis.
     */
    private suspend fun httpFailure(status: Int): FeedResult<ArticlePage> = when (status) {
        HTTP_UNAUTHORIZED -> {
            sessionStore.invalidateTokens()
            Outcome.Failure(FeedError.SessionExpired)
        }

        else -> Outcome.Failure(FeedError.Unexpected("HTTP $status"))
    }
}
