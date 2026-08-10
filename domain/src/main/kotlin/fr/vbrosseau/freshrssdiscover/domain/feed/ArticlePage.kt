package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Position in the feed, opaque to the domain.
 *
 * A relative cursor, not a rank: it names the last article already delivered,
 * and the server resumes right after it (docs/freshrss-api.md §3.5). The
 * domain must not fabricate one, hence a dedicated type rather than a bare
 * `String`.
 */
@JvmInline
value class PageCursor(val value: String)

/**
 * One page of the feed.
 *
 * [nextCursor] set to `null` means end of feed, and it is the only available
 * signal: the API returns no total count (docs/freshrss-api.md §3.5). A full
 * page without a cursor is therefore a legitimate end, not an anomaly.
 */
data class ArticlePage(
    val articles: List<Article>,
    val nextCursor: PageCursor?,
) {
    /**
     * True when more articles remain to be requested.
     *
     * Named rather than left to scattered `!= null` checks: SPECS.md §4.4
     * requires distinguishing end of feed from a loading failure, and
     * confusing the two would produce a list that simply stops growing,
     * indistinguishable from a breakdown.
     */
    val hasMore: Boolean get() = nextCursor != null
}
