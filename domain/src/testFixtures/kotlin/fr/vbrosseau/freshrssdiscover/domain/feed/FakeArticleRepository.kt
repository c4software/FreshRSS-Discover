package fr.vbrosseau.freshrssdiscover.domain.feed

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import kotlinx.coroutines.CompletableDeferred

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

    override suspend fun loadPage(cursor: PageCursor?): FeedResult<ArticlePage> {
        loadCallCount++
        requestedCursors += cursor

        return pendingLoad?.await() ?: programmed.removeFirstOrNull() ?: fallbackResult
    }

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
