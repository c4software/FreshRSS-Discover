package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Article identifier, in decimal form.
 *
 * The API exposes the same integer in two bases: hexadecimal in `items[].id`,
 * decimal in `continuation` and in the `i` parameter of `edit-tag`
 * (docs/freshrss-api.md §3.4). Conversion belongs to the `data` layer; the
 * domain knows a single form, otherwise the confusion would propagate to
 * mark-as-read, where it would fail silently.
 */
@JvmInline
value class ArticleId(val value: Long)

/**
 * Source feed of an article.
 *
 * The title travels with the article instead of being resolved at display
 * time: in a mixed feed, the source is what makes an article intelligible
 * (SPECS.md §4.3), and deferred resolution would make it appear late.
 */
data class FeedRef(
    /** Feed identifier as the API names it, e.g. `feed/12`. */
    val id: String,
    val title: String,
)

/**
 * An article of the Discover feed.
 *
 * Contains only what SPECS.md §4.3 requires to display. Full content is
 * absent: the app opens the original article in the browser (§4.7), and
 * keeping the full body of every article would bloat the cache for nothing.
 */
data class Article(
    val id: ArticleId,
    val title: String,
    /**
     * Link to the original article, `null` when unusable.
     *
     * Articles without a link exist (malformed feed, purely local content).
     * SPECS.md §4.7 then requires rendering it non-clickable rather than
     * opening an empty page.
     */
    val url: String?,
    /** Publication date, in seconds since the Unix epoch. */
    val publishedAtEpochSeconds: Long,
    /** Excerpt, possibly empty. The server already truncates it. */
    val summary: String,
    /** Illustration, `null` when the article has none. No placeholder image. */
    val imageUrl: String?,
    val author: String?,
    val feed: FeedRef,
    val isRead: Boolean,
)
