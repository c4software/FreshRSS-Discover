package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
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

private const val HTTP_UNAUTHORIZED = 401

@Singleton
internal class DefaultArticleRepository @Inject constructor(
    private val api: FreshRssApi,
    private val sessionStore: SessionStore,
    private val cache: ArticleCache,
    private val freshness: FeedFreshnessRepository,
    private val network: NetworkAvailability,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArticleRepository {
    /**
     * Dernier article rendu par la pagination, ou rien avant la première page.
     *
     * Le mélange a besoin de savoir ce qui précède pour que la règle 4 de
     * SPECS.md §4.2 tienne à la jonction entre deux pages. Cet état vit ici
     * plutôt que chez l'appelant pour une raison mécanique : `loadPage` ne
     * prend pas la fin de page en paramètre, et lui en ajouter un ferait porter
     * à l'écran une contrainte de l'algorithme de mélange, qu'il ne peut pas
     * satisfaire sans savoir combien d'éléments la règle consomme.
     *
     * Un seul article est conservé : la monotonie ne se juge qu'entre voisins
     * immédiats, retenir la page entière garderait quarante articles en mémoire
     * pour n'en lire qu'un.
     *
     * Ni verrou ni volatile : les chargements sont sérialisés par l'appelant,
     * et l'unique conséquence d'un entrelacement serait une jonction moins bien
     * répartie — jamais un état incohérent.
     */
    private var paginationTail: List<Article> = emptyList()

    override suspend fun loadPage(cursor: PageCursor?): FeedResult<ArticlePage> = withContext(ioDispatcher) {
        // Rien ne précède le début du flux : un rechargement complet doit
        // produire exactement le même ordre que le premier affichage (règle 3).
        if (cursor == null) paginationTail = emptyList()
        fetchPage(cursor, continuesPagination = true)
    }

    /**
     * Le mélange repart de zéro et la continuité de la pagination n'est pas
     * touchée : la page rendue est la tête du flux, alors que [paginationTail]
     * en décrit le bas.
     */
    override suspend fun refresh(): FeedResult<ArticlePage> = withContext(ioDispatcher) {
        fetchPage(cursor = null, continuesPagination = false)
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

    override fun observeCachedArticles(limit: Int): Flow<List<Article>> =
        cache.observeArticles(limit).map { articles ->
            interleaveBySource(articles.filterNot(Article::isRead))
        }

    private suspend fun fetchPage(
        cursor: PageCursor?,
        continuesPagination: Boolean,
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
        ).toFeedResult(continuesPagination)
    }

    /**
     * Applique le mélange (SPECS.md §4.2) à la page rendue.
     *
     * Le cache, lui, reçoit l'ordre du serveur : il est relu trié par date, et
     * le mélange y est réappliqué à la lecture. Persister un ordre déjà mélangé
     * n'apporterait rien et le figerait alors que de nouveaux articles doivent
     * pouvoir s'y insérer.
     */
    private fun ArticlePage.interleaved(continuesPagination: Boolean): ArticlePage {
        val ordered = interleaveBySource(articles, if (continuesPagination) paginationTail else emptyList())
        if (continuesPagination) paginationTail = listOfNotNull(ordered.lastOrNull())
        return copy(articles = ordered)
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
        continuesPagination: Boolean,
    ): FeedResult<ArticlePage> = when (this) {
        is ApiOutcome.Success -> {
            val page = value.toArticlePage()
            cache.save(page.articles)
            freshness.recordRefresh()
            Outcome.Success(page.interleaved(continuesPagination))
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
