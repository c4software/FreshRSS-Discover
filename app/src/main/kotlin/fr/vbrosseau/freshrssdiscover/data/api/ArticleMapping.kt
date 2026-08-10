package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedRef
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import java.lang.Long.parseUnsignedLong

/** Google Reader legacy prefix, always present before the identifier. */
private const val ITEM_ID_PREFIX = "tag:google.com,2005:reader/item/"

/** Category carrying the read state. Its absence means unread. */
private const val READ_CATEGORY = "user/-/state/com.google/read"

private const val HEXADECIMAL = 16

/** HTML tags, including their attributes. */
private val HTML_TAG = Regex("<[^>]*>")

/** First image in the content, regardless of attribute order. */
private val IMG_SOURCE = Regex("""<img[^>]*\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private val WHITESPACE = Regex("\\s+")

/**
 * Converts a `stream/contents` response into a domain page.
 *
 * Articles whose identifier cannot be parsed are dropped rather than failing
 * the whole page: one broken article must not cost the user the other
 * thirty-nine.
 */
internal fun StreamContentsDto.toArticlePage(): ArticlePage = ArticlePage(
    articles = items.mapNotNull(ItemDto::toArticleOrNull),
    // The cursor is absent when the stream is exhausted: this is the only
    // end-of-stream signal, as the API returns no total count.
    nextCursor = continuation?.takeIf(String::isNotBlank)?.let(::PageCursor),
)

internal fun ItemDto.toArticleOrNull(): Article? {
    val articleId = parseArticleId(id) ?: return null
    val rawSummary = summary?.content ?: content?.content.orEmpty()

    return Article(
        id = articleId,
        title = title.stripHtml(),
        url = firstUsableLink(),
        publishedAtEpochSeconds = published,
        summary = rawSummary.stripHtml(),
        imageUrl = illustrationOf(rawSummary),
        author = author?.stripHtml()?.takeIf(String::isNotBlank),
        feed = FeedRef(id = origin.streamId, title = origin.title.stripHtml()),
        isRead = READ_CATEGORY in categories,
    )
}

/**
 * Normalizes the identifier to its decimal form.
 *
 * The API exposes it in hexadecimal here, but in decimal in `continuation` and
 * in the `i` parameter of `edit-tag`. Keeping a single base above this layer
 * prevents the confusion from reaching mark-as-read, where it would fail
 * silently.
 *
 * `parseUnsignedLong` rather than `toLong`: FreshRSS produces an unsigned
 * 64-bit integer, and an identifier above `Long.MAX_VALUE` would make `toLong`
 * throw.
 *
 * Consequence to keep in mind: an identifier above `Long.MAX_VALUE` is stored
 * as raw bits, hence negative in Kotlin. Reformatting it with `toString()` for
 * an `edit-tag` would send `-1` to the server; `java.lang.Long.toUnsignedString`
 * must be used instead. See TASKS.md, GOAL-008.
 */
private fun parseArticleId(raw: String): ArticleId? {
    val digits = raw.removePrefix(ITEM_ID_PREFIX).trim()
    return runCatching { ArticleId(parseUnsignedLong(digits, HEXADECIMAL)) }.getOrNull()
}

/**
 * `canonical` first, `alternate` second.
 *
 * Both carry the same value in practice, but `canonical` is the field Google
 * Reader intended for this use. `null` when no link is usable: SPECS.md §4.7
 * requires a non-clickable article in that case, not opening an empty page.
 */
private fun ItemDto.firstUsableLink(): String? =
    (canonical + alternate).map(LinkDto::href).firstOrNull(String::isNotBlank)

/**
 * Article illustration: `enclosure` first, content second.
 *
 * Settles SPECS.md §8 question 6. The order reflects reliability: an
 * `enclosure` is an illustration declared by the feed, whereas an `<img>` tag
 * in the content may be a tracking pixel, a footer logo, or a share button.
 * The content is only a fallback: many feeds emit no `enclosure`, and leaving
 * those articles without an illustration would noticeably impoverish the
 * Discover feed.
 */
private fun ItemDto.illustrationOf(rawSummary: String): String? =
    enclosure.firstOrNull { it.isImage() }?.href?.takeIf(String::isNotBlank)
        ?: IMG_SOURCE.find(rawSummary)?.groupValues?.get(1)?.takeIf(String::isNotBlank)

/**
 * `startsWith("image")` rather than `== "image/..."`: when the source feed
 * gives no MIME type, FreshRSS falls back to the bare word `image`.
 */
private fun EnclosureDto.isImage(): Boolean = type?.startsWith("image", ignoreCase = true) == true

/**
 * Reduces an HTML fragment to its text content.
 *
 * FreshRSS fields contain HTML: displaying it as-is would show tags to the
 * user, and letting a text component interpret it would open the door to
 * uncontrolled third-party content. An excerpt only needs the text.
 */
private fun String.stripHtml(): String = HTML_TAG.replace(this, " ")
    .decodeHtmlEntities()
    .replace(WHITESPACE, " ")
    .trim()

/**
 * Decodes only the entities RSS feeds actually produce.
 *
 * `&amp;` is handled last: the reverse order would turn `&amp;lt;` into `<`,
 * reintroducing a tag that was just neutralized.
 */
private fun String.decodeHtmlEntities(): String = this
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
