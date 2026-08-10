package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlinx.coroutines.flow.Flow

/**
 * Number of articles to request from [ArticleRepository.observeCachedArticles].
 *
 * Five pages: enough to restore several screens of scrolling at launch,
 * without reading the whole cache to display only its top. The bound is
 * essential: the cache is only purged of its read articles (SPECS.md §5.4), so
 * a prolific feed would accumulate thousands of rows to load before the first
 * frame.
 *
 * Passed explicitly rather than as a parameter default: an interface default
 * generates a bridge method nothing executes, which would show up as
 * uncovered in a module verified at 98%.
 */
const val CACHED_FEED_LIMIT = 200

/**
 * Access to the feed's articles.
 *
 * Declared here, implemented in `:app`: the domain expresses what it needs
 * without knowing anything about HTTP or storage (ARCHITECTURE.md §2).
 *
 * The repository holds no state. It returns pages already shuffled and
 * accumulates neither list nor position: the displayed list, the cursor, and
 * the tail of the previous page belong to the caller. Only the caller knows
 * what is actually on screen (SPECS.md §4.6 requires preserving the reading
 * position), and since the repository is a singleton shared by both
 * presentation modes, storing the page tail here would let one mode's
 * pagination contaminate the other's page junction.
 */
interface ArticleRepository {
    /**
     * Fetches a page of unread articles, already shuffled (SPECS.md §4.2).
     *
     * The returned order is the display order: the caller must not reorder,
     * otherwise rule 4 would break. Only the caller sees the junction between
     * two pages, but only the repository knows how the previous one was
     * ordered.
     *
     * @param cursor position returned by the previous page. `null` requests
     *   the start of the feed, and only `null`: fabricating an empty cursor
     *   would silently restart from the first page
     *   (docs/freshrss-api.md §3.5).
     * @param previousTail the tail of the previous page as rendered; its last
     *   article is enough. This is what upholds rule 4 of SPECS.md §4.2 at the
     *   junction between two pages: monotonicity is only judged between
     *   immediate neighbours. Empty for the first page, and empty after a
     *   reload whose tail is that of the refreshed page; the tail follows the
     *   same path as the cursor.
     */
    suspend fun loadPage(
        cursor: PageCursor? = null,
        previousTail: List<Article> = emptyList(),
    ): FeedResult<ArticlePage>

    /**
     * The [limit] unread articles from the cache, shuffled, newest first.
     *
     * Deliberately serves two needs with one flow:
     *
     * - at launch (SPECS.md §5.1), the feed displays immediately, before any
     *   request completes;
     * - offline (SPECS.md §5.2), it remains readable after the request failed.
     *
     * This is why a cache-backed page has no cursor: it is never returned as
     * an [ArticlePage]. A `null` `nextCursor` means end of feed and nothing
     * else (see [ArticlePage]); dressing the cache up as a page would show
     * "you have read everything" to a user who merely lost network. The cache
     * is a parallel, permanent source, and network failures are still reported
     * as-is by [loadPage]; the caller signals them without alarm, since it has
     * content to show.
     *
     * The flow emits on every cache write: a stored network page propagates by
     * itself.
     */
    fun observeCachedArticles(limit: Int): Flow<List<Article>>

    /**
     * Requests the start of the feed again (SPECS.md §4.6).
     *
     * Returns the first page as it stands today, shuffled among its own
     * articles only: nothing precedes it. It therefore also contains articles
     * already displayed; the API has no "since last time" notion
     * (docs/freshrss-api.md §3.5).
     *
     * The caller prepends the articles it does not know yet and leaves the
     * rest in place: refreshing must not reorder what is already displayed
     * (rule 3 of SPECS.md §4.2). Deduplication is the caller's job for the
     * same reason as accumulation: only the caller knows what is on screen.
     *
     * Does not affect pagination continuity: cursor and page tail live in the
     * caller, which decides whether to resume from the refreshed page.
     */
    suspend fun refresh(): FeedResult<ArticlePage>

    /**
     * What remains to read in the cache, without touching the network.
     *
     * No `FeedResult`: there is no failure to report, an empty cache is well
     * expressed by an empty list. This differs from [loadPage], which can find
     * the server unreachable.
     *
     * Staying off the network is the contract, not a convenience: the caller
     * is the reading reminder (SPECS.md §4.9), and SPECS.md §2 always excludes
     * background synchronization. An implementation that fetched a page would
     * issue a request without a user action (§7.4).
     */
    suspend fun unreadFromCache(limit: Int): List<Article>
}
