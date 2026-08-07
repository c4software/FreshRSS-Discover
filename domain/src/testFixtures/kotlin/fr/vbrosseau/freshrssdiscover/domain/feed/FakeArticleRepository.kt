package fr.vbrosseau.freshrssdiscover.domain.feed

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Dépôt d'articles piloté, pour les tests.
 *
 * Les issues sont **programmées d'avance** et consommées dans l'ordre : le flux
 * Discover s'éprouve page après page, et un `nextResult` unique obligerait
 * chaque test à réarmer le dépôt entre deux chargements — donc à connaître le
 * moment exact où la coroutine reprend la main.
 *
 * [pendingLoad] permet de suspendre un chargement en cours, sur le modèle de
 * `FakeAuthRepository.pendingSignIn` : sans cela, l'état intermédiaire — celui
 * où l'utilisateur voit l'indicateur de progression en bas du flux — serait
 * inobservable, et l'idempotence de `loadMore()` invérifiable.
 */
class FakeArticleRepository : ArticleRepository {
    private val programmed = ArrayDeque<FeedResult<ArticlePage>>()

    /** Issue servie une fois [programmed] épuisée : une fin de flux vide. */
    var fallbackResult: FeedResult<ArticlePage> = Outcome.Success(ArticlePage(emptyList(), null))

    /** Arme un chargement qui ne se terminera qu'une fois [completeLoad] appelée. */
    var pendingLoad: CompletableDeferred<FeedResult<ArticlePage>>? = null

    var loadCallCount: Int = 0
        private set

    /**
     * Curseurs reçus, dans l'ordre.
     *
     * Le premier vaut `null` : seul `null` demande le début du flux, et
     * fabriquer un curseur vide relancerait la première page en silence.
     */
    val requestedCursors: MutableList<PageCursor?> = mutableListOf()

    var refreshCallCount: Int = 0
        private set

    /**
     * Contenu du cache, modifiable en cours de test.
     *
     * Un `MutableStateFlow` plutôt qu'une liste figée : le flux du cache émet à
     * chaque écriture, et un écran qui ne se mettrait à jour qu'au premier
     * émis passerait le test sans que rien ne le trahisse.
     */
    val cachedArticles: MutableStateFlow<List<Article>> = MutableStateFlow(emptyList())

    override suspend fun loadPage(cursor: PageCursor?): FeedResult<ArticlePage> {
        loadCallCount++
        requestedCursors += cursor

        return nextResult()
    }

    /**
     * Sert la même file que [loadPage] : un test de rafraîchissement programme
     * ses pages sans avoir à savoir par quelle méthode elles seront demandées.
     */
    override suspend fun refresh(): FeedResult<ArticlePage> {
        refreshCallCount++

        return nextResult()
    }

    override fun observeCachedArticles(limit: Int): Flow<List<Article>> =
        cachedArticles.map { articles -> articles.take(limit) }

    private suspend fun nextResult(): FeedResult<ArticlePage> =
        pendingLoad?.await() ?: programmed.removeFirstOrNull() ?: fallbackResult

    /** Programme une page, avec son curseur de suite — `null` pour une fin de flux. */
    fun enqueuePage(
        articles: List<Article>,
        nextCursor: PageCursor? = null,
    ) {
        programmed += Outcome.Success(ArticlePage(articles, nextCursor))
    }

    fun enqueueFailure(error: FeedError) {
        programmed += Outcome.Failure(error)
    }

    /** Débloque le chargement armé par [pendingLoad], et le désarme. */
    fun completeLoad(result: FeedResult<ArticlePage>) {
        val pending = checkNotNull(pendingLoad) { "aucun chargement en attente" }
        pendingLoad = null
        pending.complete(result)
    }
}
