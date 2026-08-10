package fr.vbrosseau.freshrssdiscover.domain.feed

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Scripted article repository for tests.
 *
 * Results are programmed in advance and consumed in order: the Discover feed
 * is exercised page after page, and a single `nextResult` would force each
 * test to re-arm the repository between loads, i.e. to know the exact moment
 * the coroutine resumes.
 *
 * [pendingLoad] suspends an in-flight load, following the model of
 * `FakeAuthRepository.pendingSignIn`: without it, the intermediate state where
 * the user sees the progress indicator at the bottom of the feed would be
 * unobservable, and the idempotence of `loadMore()` unverifiable.
 */
class FakeArticleRepository : ArticleRepository {
    private val programmed = ArrayDeque<FeedResult<ArticlePage>>()

    /** Result served once [programmed] is exhausted: an empty end of feed. */
    var fallbackResult: FeedResult<ArticlePage> = Outcome.Success(ArticlePage(emptyList(), null))

    /** Arms a load that only completes once [completeLoad] is called. */
    var pendingLoad: CompletableDeferred<FeedResult<ArticlePage>>? = null

    /**
     * Arms a refresh that only completes once [completeRefresh] is called.
     *
     * Kept separate from [pendingLoad]: the distinction is what allows staging
     * the GOAL-028 race, a page in flight while a refresh completes. A single
     * latch would suspend both calls together and make the arrival order,
     * which is the whole point of the test, unobservable.
     */
    var pendingRefresh: CompletableDeferred<FeedResult<ArticlePage>>? = null

    var loadCallCount: Int = 0
        private set

    /**
     * Cursors received, in order.
     *
     * The first is `null`: only `null` requests the start of the feed, and
     * fabricating an empty cursor would silently re-request the first page.
     */
    val requestedCursors: MutableList<PageCursor?> = mutableListOf()

    var refreshCallCount: Int = 0
        private set

    /**
     * Cache contents, mutable mid-test.
     *
     * A `MutableStateFlow` rather than a frozen list: the cache flow emits on
     * every write, and a screen that only updated on the first emission would
     * pass the test without anything revealing it.
     */
    val cachedArticles: MutableStateFlow<List<Article>> = MutableStateFlow(emptyList())

    /** Each page tail received, in order: shuffle continuity is observed here. */
    val requestedTails: MutableList<List<Article>> = mutableListOf()

    override suspend fun loadPage(
        cursor: PageCursor?,
        previousTail: List<Article>,
    ): FeedResult<ArticlePage> {
        loadCallCount++
        requestedCursors += cursor
        requestedTails += previousTail

        return nextResult()
    }

    /**
     * Observes the state of the world at the moment the refresh starts.
     *
     * A call counter says that something happened, never that it happened
     * before something else: two `assertEquals` on two counters pass in any
     * order. Order is exactly what matters for refresh, which must flush
     * pending markings before querying the server (GOAL-024). This hook is
     * the only place a test can observe that from.
     */
    var onRefresh: (() -> Unit)? = null

    /**
     * Serves the same queue as [loadPage]: a refresh test programs its pages
     * without having to know which method will request them.
     */
    override suspend fun refresh(): FeedResult<ArticlePage> {
        refreshCallCount++
        onRefresh?.invoke()

        return pendingRefresh?.await() ?: dequeue()
    }

    /** What the cache returns for the reading reminder (SPECS.md §4.9). */
    var unreadInCache: List<Article> = emptyList()

    override suspend fun unreadFromCache(limit: Int): List<Article> = unreadInCache.take(limit)

    override fun observeCachedArticles(limit: Int): Flow<List<Article>> =
        cachedArticles.map { articles -> articles.take(limit) }

    private suspend fun nextResult(): FeedResult<ArticlePage> = pendingLoad?.await() ?: dequeue()

    private fun dequeue(): FeedResult<ArticlePage> = programmed.removeFirstOrNull() ?: fallbackResult

    /** Programs a page with its continuation cursor; `null` marks end of feed. */
    fun enqueuePage(
        articles: List<Article>,
        nextCursor: PageCursor? = null,
    ) {
        programmed += Outcome.Success(ArticlePage(articles, nextCursor))
    }

    fun enqueueFailure(error: FeedError) {
        programmed += Outcome.Failure(error)
    }

    /** Completes the load armed by [pendingLoad] and disarms it. */
    fun completeLoad(result: FeedResult<ArticlePage>) {
        val pending = checkNotNull(pendingLoad) { "aucun chargement en attente" }
        pendingLoad = null
        pending.complete(result)
    }

    /** Completes the refresh armed by [pendingRefresh] and disarms it. */
    fun completeRefresh(result: FeedResult<ArticlePage>) {
        val pending = checkNotNull(pendingRefresh) { "aucun rechargement en attente" }
        pendingRefresh = null
        pending.complete(result)
    }
}
