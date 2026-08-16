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
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
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
 * Number of articles requested per page.
 *
 * Settles SPECS.md §8 question 1. Measured on a real feed: median summary of
 * 1,324 characters, 90th percentile at 4,379 — a page of 40 therefore weighs
 * about 55 KB. Enough lookahead to keep scrolling uninterrupted without
 * delaying the first display.
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
        // Nothing precedes the start of the stream: a full reload must produce
        // exactly the same order as the first display (rule 3).
        val tail = if (cursor == null) emptyList() else previousTail
        fetchPage(cursor, previousTail = tail, renewsCache = false)
    }

    /**
     * The shuffle starts from scratch: the returned page is the head of the
     * feed, nothing precedes it.
     *
     * The cache is also renewed, which it previously was not (GOAL-026): the
     * reload cleared the display without touching the database, so killing and
     * relaunching the application resurrected the feed that had just been
     * exhausted. See [renewCache].
     */
    override suspend fun refresh(): FeedResult<ArticlePage> = withContext(ioDispatcher) {
        fetchPage(cursor = null, previousTail = emptyList(), renewsCache = true)
    }

    /**
     * Read articles are filtered out here, since the query cannot do it.
     *
     * The cache keeps them until the purge (SPECS.md §5.4) while the feed only
     * shows unread items (§4.1). The limit therefore applies before filtering:
     * a cache loaded with read articles returns a list shorter than requested
     * — harmless, as the first network page immediately completes it.
     */
    override suspend fun unreadFromCache(limit: Int): List<Article> =
        // Shuffled like the feed (SPECS.md §4.2): the reminder quotes the first
        // titles, and taking them in raw database order would always quote the
        // most talkative source.
        interleaveBySource(cache.unreadArticles(limit))

    /**
     * The shuffled cache (SPECS.md §4.2), read articles included.
     *
     * This is what makes the launch stable: the set does not change between
     * openings, so the shuffle yields the same order. Excluding read articles
     * changed that set every session — marking consumes some — and the feed
     * appeared to reshuffle itself with no request sent. Read articles stay
     * displayed, grayed in place, until the next user-requested reload
     * (SPECS.md §4.6), which alone renews the list.
     *
     * The reading reminder only wants unread items: that is
     * [unreadFromCache], whose filter is done by SQLite.
     */
    override fun observeCachedArticles(limit: Int): Flow<List<Article>> =
        cache.observeArticles(limit).map(::interleaveBySource)

    // No shuffle, unlike the two above: the caller's order IS the point.
    override suspend fun cachedByIds(ids: List<ArticleId>): List<Article> = cache.articlesByIds(ids)

    private suspend fun fetchPage(
        cursor: PageCursor?,
        previousTail: List<Article>,
        renewsCache: Boolean,
    ): FeedResult<ArticlePage> {
        /*
         * No session: the root router should already have switched to the
         * sign-in screen. Still report it rather than returning an empty page,
         * which would read as "no more articles".
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
     * The reload renews the cache: the server response becomes its content
     * (SPECS.md §4.6, GOAL-027).
     *
     * Without this, the reload cleared the display but left the database
     * intact: reading everything, reloading — the screen said there was
     * nothing left — then killing and relaunching the application resurrected
     * the previous set. Since GOAL-020 there is no read flag: those ghosts
     * were indistinguishable from genuinely unread articles.
     *
     * GOAL-026 purged what was locally read, and that was not enough. Measured
     * on device: after a reload showing "nothing to read", the cache kept 31
     * rows unread locally that the server no longer returned. They had been
     * read elsewhere — web UI, another client — and
     * `upsertPreservingLocalReadState` only propagates the read state for
     * articles the server returns: absence said nothing. Yet absence is the
     * only signal the application ever receives of a reading made elsewhere.
     *
     * The criterion is therefore membership in the returned page, never the
     * local read state. Rows whose mark is still awaiting transmission are
     * spared: they carry a truth the server does not know, so it cannot return
     * it, and deleting them would bring back as new what the user just read.
     *
     * After the save, never before: `upsertPreservingLocalReadState` reads the
     * read state from these very rows.
     *
     * Pagination renews nothing: that would erase the feed under the reader's
     * eyes, since a next page never contains what precedes it. Only a
     * user-requested reload renews, as SPECS.md §4.6 states — the accepted
     * cost being that the offline reserve then falls back to the head page.
     */
    private suspend fun renewCache(returned: List<Article>) {
        cache.retainOnly(returned.map(Article::id))
    }

    /**
     * Restores the page to publication order, then applies the shuffle
     * (SPECS.md §4.2).
     *
     * The sort is not a precaution; it fixes a defect seen on screen. The
     * server sorts its list by retrieval date, not publication: observed on a
     * real instance, an article published two days earlier opened the first
     * page. Displayed as-is, that page installed an order different from the
     * cache's — sorted by publication — and the launch screen then depended on
     * whether disk or network answered first: each startup drew its order at
     * random. Resume-reading (SPECS.md §5.3), which looks for "the first
     * article no more recent", also landed anywhere in a non-chronological
     * list.
     *
     * The tie-break on equal dates matches the cache's SQL sort — descending
     * id — so both sources produce exactly the same order. The shuffle expects
     * reverse-chronological input: that is its contract, which retrieval order
     * violated.
     *
     * The cache receives the server order: it is re-read sorted by date, and
     * the shuffle is reapplied on read. Persisting an already shuffled order
     * would gain nothing and would freeze it while new articles must be able
     * to slot in.
     */
    private fun ArticlePage.interleaved(previousTail: List<Article>): ArticlePage {
        val chronological = articles.sortedWith(
            compareByDescending<Article> { it.publishedAtEpochSeconds }
                .thenByDescending { it.id.value },
        )
        return copy(articles = interleaveBySource(chronological, previousTail))
    }

    /**
     * Every fetched page is written to the cache before being returned.
     *
     * This is what lets the feed display immediately at the next launch,
     * without waiting for the network (SPECS.md §5.1). The write happens here
     * rather than in the caller: a caller forgetting it would produce an
     * incomplete cache, and the defect would only show at the next launch.
     *
     * The server contact date is recorded in the same place, for the same
     * reason. It is what will later say whether the displayed feed is stale
     * (SPECS.md §4.6). Two ViewModels request pages; recording it in each
     * would duplicate the rule and let the two presentation modes diverge.
     * The layer that talked to the server is the only one that knows it
     * answered.
     *
     * A valid but empty page counts as a response: the server spoke, the feed
     * simply has nothing new. A failure records nothing — otherwise an
     * application left open offline all day would appear fresh.
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
         * Connectivity is only read here, at the moment of failure: checking
         * it beforehand would give a stale answer, as the network can vanish
         * during the request — exactly the case to diagnose.
         */
        is ApiOutcome.TransportError -> Outcome.Failure(
            if (network.isOnline()) FeedError.ServerUnreachable else FeedError.NoNetwork,
        )
    }

    /**
     * A `401` on a read means the server rejects the token — the user changed
     * their API password.
     *
     * The session is wiped here, which makes the root router switch on its
     * own: no screen has a redirect to trigger (SPECS.md §3.4). The sign-in
     * hint survives: address and username remain prefilled.
     */
    private suspend fun httpFailure(status: Int): FeedResult<ArticlePage> = when (status) {
        HTTP_UNAUTHORIZED -> {
            sessionStore.invalidateTokens()
            Outcome.Failure(FeedError.SessionExpired)
        }

        else -> Outcome.Failure(FeedError.Unexpected("HTTP $status"))
    }
}
