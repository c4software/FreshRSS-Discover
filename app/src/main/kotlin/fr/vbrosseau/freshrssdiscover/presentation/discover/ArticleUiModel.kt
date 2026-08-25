package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.presentation.feed.truncatedAtWord

/**
 * Maximum excerpt length, in characters.
 *
 * The server sends the full summary: 1,324 characters median, 34,777 maximum
 * measured (SPECS.md §8, question 7). Keeping it whole would make Compose
 * measure a paragraph of several thousand characters to display three lines,
 * on every card and every recomposition.
 *
 * 240 is calibrated on what the card actually shows: three lines of
 * `bodyMedium` at 411 dp width hold about 180 characters, and up to 210 at
 * the smallest system font size. The margin guarantees the visible cut stays
 * Compose's ellipsis at the end of the third line, not text stopping abruptly
 * mid second line.
 */
const val EXCERPT_MAX_LENGTH = 240

/**
 * An article as the list displays it.
 *
 * Everything is already decided: the excerpt is truncated, the date is
 * reduced to an age, link presence is a boolean. A Composable displays this
 * state, it does not derive it (AGENTS.md §9).
 */
data class ArticleUiModel(
    /** Raw form of the identifier: the list's stable key. */
    val id: Long,
    val title: String,
    /** Without it, the mix of sources would be confusing (SPECS.md §4.3). */
    val feedTitle: String,
    val publishedAt: RelativeTime,
    val excerpt: String,
    /**
     * Where to fetch the illustration, `null` when the article announces
     * none.
     *
     * The URL is carried as the server provided it: validating or normalizing
     * it would be a computation, which does not belong to display.
     */
    val imageUrl: String? = null,
    /**
     * True when the article announces an illustration, so the card reserves a
     * slot for it.
     *
     * Distinct from [imageUrl], which only says where to fetch it: the two
     * only diverge in tests and previews, where the illustrated card is
     * wanted without triggering a request. The projection below always keeps
     * them consistent.
     */
    val hasIllustration: Boolean = imageUrl != null,
    /**
     * Original link, `null` when the feed provided no usable one.
     *
     * Carried as the server gave it: `ArticleOpener` decides what is openable
     * and revalidates whatever it is passed anyway.
     */
    val url: String? = null,
    /**
     * False when the article has no usable link.
     *
     * SPECS.md §4.7 then requires making it non-clickable while still showing
     * it: opening an empty page would be worse than offering nothing.
     *
     * Derived from [url] by default, for the same reason as
     * [hasIllustration]: a test or preview sometimes wants the clickable card
     * without providing an address.
     */
    val isOpenable: Boolean = url != null,
    /**
     * True for an article already read, whether in this session or a
     * previous one (SPECS.md §4.5).
     *
     * Both origins count: projected without this state, an article read the
     * day before arrived from the cache as new, and its badge only appeared
     * after one second of visibility, once the session's marking restored it.
     *
     * A marked article stays in the list and in place: the flag only states
     * the truth about its state, never removes it, which would shift content
     * mid-read.
     */
    val isRead: Boolean = false,
)

/**
 * Projects a domain article into its displayable form.
 *
 * @param nowEpochMillis reference instant, provided by `Clock`.
 */
fun Article.toUiModel(nowEpochMillis: Long): ArticleUiModel = ArticleUiModel(
    id = id.value,
    title = title,
    feedTitle = feed.title,
    publishedAt = relativeTimeSince(publishedAtEpochSeconds, nowEpochMillis),
    excerpt = summary.toExcerpt(),
    imageUrl = imageUrl,
    url = url,
    isRead = isRead,
)

/** Word-boundary truncation lives in `truncatedAtWord`, shared with the immersive mode. */
private fun String.toExcerpt(): String = truncatedAtWord(EXCERPT_MAX_LENGTH)
