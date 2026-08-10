package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DTOs are built directly rather than decoded from JSON: deserialization is
 * already covered by `StreamContentsDtoTest`, and involving it here would
 * fail these tests for reasons unrelated to the mapper.
 *
 * Each case corresponds to an observed API pitfall (docs/freshrss-api.md
 * §3.4).
 */
class ArticleMappingTest {
    /**
     * Ordinary article; each test modifies only the field it exercises, so
     * the case's subject is visible on reading.
     */
    private val item = ItemDto(
        id = "tag:google.com,2005:reader/item/0000000000000001",
        title = "Un titre",
        published = 1_699_999_000L,
        canonical = listOf(LinkDto("https://exemple.org/article")),
        summary = ContentDto("<p>Un extrait.</p>"),
        origin = OriginDto(streamId = "feed/12", title = "Exemple"),
        author = "Alice",
    )

    private fun page(vararg items: ItemDto, continuation: String? = null) =
        StreamContentsDto(items = items.toList(), continuation = continuation).toArticlePage()

    // ----- Identifier --------------------------------------------------------

    @Test
    fun theHexadecimalIdentifierIsBroughtBackToItsDecimalForm() {
        // The API exposes the same integer in hexadecimal here and in decimal
        // in `continuation` and in the `i` parameter of `edit-tag`. Keeping a
        // single base prevents the confusion from reaching mark-as-read,
        // where it would fail without telling the user.
        val article = item.copy(id = "tag:google.com,2005:reader/item/0006587a0aa0dde5").toArticleOrNull()

        assertEquals(1_786_131_047_833_061L, assertNotNull(article).id.value)
    }

    @Test
    fun anIdentifierBeyondLongMaxValueIsStillRead() {
        // FreshRSS produces an unsigned 64-bit integer: `toLong` would throw
        // here and the article would vanish from the feed with no visible
        // cause.
        val article = item.copy(id = "tag:google.com,2005:reader/item/ffffffffffffffff").toArticleOrNull()

        assertNotNull(article)
    }

    @Test
    fun anUnreadableIdentifierDiscardsOnlyItsOwnArticle() {
        // One malformed article must not cost the user the other thirty-nine
        // on the same page.
        val articles = page(
            item.copy(id = ""),
            item.copy(id = "tag:google.com,2005:reader/item/zzz", title = "Illisible"),
            item.copy(id = "tag:google.com,2005:reader/item/0000000000000002", title = "Lisible"),
        ).articles

        assertEquals(listOf("Lisible"), articles.map { it.title })
    }

    // ----- Pagination --------------------------------------------------------

    @Test
    fun aContinuationTokenBecomesTheNextCursor() {
        val articlePage = page(item, continuation = "45219")

        assertEquals(PageCursor("45219"), articlePage.nextCursor)
        assertTrue(articlePage.hasMore)
    }

    @Test
    fun anAbsentOrEmptyContinuationMeansTheEndOfTheFeed() {
        // The only end-of-feed signal: the API returns no total count. An
        // empty string counts as absent, otherwise the next page would be
        // requested indefinitely.
        val absent = page(item, continuation = null)
        val empty = page(item, continuation = "")

        assertNull(absent.nextCursor)
        assertFalse(absent.hasMore)
        assertNull(empty.nextCursor)
        assertFalse(empty.hasMore)
    }

    // ----- Read state --------------------------------------------------------

    @Test
    fun theReadCategoryMakesTheArticleRead() {
        val article = item.copy(
            categories = listOf(
                "user/-/state/com.google/reading-list",
                "user/-/state/com.google/read",
            ),
        ).toArticleOrNull()

        assertTrue(assertNotNull(article).isRead)
    }

    @Test
    fun theAbsenceOfTheReadCategoryMeansUnread() {
        // `…/unread` is never emitted in this mode: only absence expresses it.
        val article = item.copy(categories = listOf("user/-/state/com.google/reading-list")).toArticleOrNull()

        assertFalse(assertNotNull(article).isRead)
    }

    @Test
    fun plainTextUserLabelsDoNotDisturbTheReadDetection() {
        // Observed on a real instance: `categories` mixes state identifiers
        // with bare-text user labels, no prefix. A loose detection would take
        // them for states.
        val unread = item.copy(categories = listOf("AirPods Ultra", "iPhone Ultra")).toArticleOrNull()
        val read = item.copy(
            categories = listOf(
                "AirPods Ultra",
                "user/-/state/com.google/read",
                "iPhone Ultra",
            ),
        ).toArticleOrNull()

        assertFalse(assertNotNull(unread).isRead)
        assertTrue(assertNotNull(read).isRead)
    }

    // ----- Link --------------------------------------------------------------

    @Test
    fun theCanonicalLinkWinsOverTheAlternateOne() {
        val article = item.copy(
            canonical = listOf(LinkDto("https://exemple.org/canonique")),
            alternate = listOf(LinkDto("https://exemple.org/alternatif")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/canonique", assertNotNull(article).url)
    }

    @Test
    fun anEmptyOrAbsentCanonicalFallsBackToTheAlternateLink() {
        val empty = item.copy(
            canonical = listOf(LinkDto("")),
            alternate = listOf(LinkDto("https://exemple.org/alternatif")),
        ).toArticleOrNull()
        val absent = item.copy(
            canonical = emptyList(),
            alternate = listOf(LinkDto("https://exemple.org/alternatif")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/alternatif", assertNotNull(empty).url)
        assertEquals("https://exemple.org/alternatif", assertNotNull(absent).url)
    }

    @Test
    fun anArticleWithoutAnyUsableLinkIsNotClickable() {
        // SPECS.md §4.7 requires a non-clickable article in that case, not
        // opening a blank page.
        val none = item.copy(canonical = emptyList(), alternate = emptyList()).toArticleOrNull()
        val blank = item.copy(canonical = listOf(LinkDto("")), alternate = listOf(LinkDto(""))).toArticleOrNull()

        assertNull(assertNotNull(none).url)
        assertNull(assertNotNull(blank).url)
    }

    // ----- Illustration ------------------------------------------------------

    @Test
    fun anImageEnclosureIsKeptAsTheIllustration() {
        val article = item.copy(
            enclosure = listOf(EnclosureDto(href = "https://exemple.org/image.jpg", type = "image/jpeg")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/image.jpg", assertNotNull(article).imageUrl)
    }

    @Test
    fun aBareImageEnclosureTypeIsAlsoAnIllustration() {
        // FreshRSS falls back to the bare word `image` when the source feed
        // gives no MIME type: requiring "image/..." would miss those feeds.
        val article = item.copy(
            enclosure = listOf(EnclosureDto(href = "https://exemple.org/i.png", type = "image")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/i.png", assertNotNull(article).imageUrl)
    }

    @Test
    fun aNonImageEnclosureIsIgnored() {
        // An attached podcast must not end up displayed as a thumbnail.
        val article = item.copy(
            summary = ContentDto("""<p>Texte</p><img src="https://exemple.org/vignette.jpg">"""),
            enclosure = listOf(EnclosureDto(href = "https://exemple.org/son.mp3", type = "audio/mpeg")),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/vignette.jpg", assertNotNull(article).imageUrl)
    }

    @Test
    fun withoutAnyEnclosureTheFirstImageOfTheSummaryIsUsed() {
        // Common case, observed on a real instance: many feeds emit no
        // `enclosure`. Relying on it alone would noticeably impoverish the
        // Discover feed.
        val article = item.copy(
            summary = ContentDto(
                """<p>Intro</p><img src="https://exemple.org/premiere.jpg"><img src="https://exemple.org/autre.jpg">""",
            ),
            enclosure = emptyList(),
        ).toArticleOrNull()

        assertEquals("https://exemple.org/premiere.jpg", assertNotNull(article).imageUrl)
    }

    @Test
    fun anArticleWithoutAnyImageHasNoIllustration() {
        // No placeholder image: SPECS.md provides for no invented thumbnail.
        val article = item.copy(summary = ContentDto("<p>Rien qu'du texte.</p>")).toArticleOrNull()

        assertNull(assertNotNull(article).imageUrl)
    }

    // ----- HTML cleanup ------------------------------------------------------

    @Test
    fun htmlMarkupIsReducedToItsText() {
        // Displaying markup verbatim would show angle brackets to the user;
        // interpreting it would open the door to uncontrolled third-party
        // content.
        val article = item.copy(
            title = "<h1>Bonjour   <b>monde</b></h1>",
            summary = ContentDto("<p>Bonjour <b>monde</b></p>"),
        ).toArticleOrNull()

        assertEquals("Bonjour monde", assertNotNull(article).summary)
        assertEquals("Bonjour monde", article.title)
    }

    @Test
    fun theEntitiesProducedByRssFeedsAreDecoded() {
        val article = item.copy(
            summary = ContentDto("Tom&nbsp;&amp;&nbsp;Jerry &lt;i&gt; &quot;cite&quot; &#39;apostrophe&#39;"),
        ).toArticleOrNull()

        assertEquals("""Tom & Jerry <i> "cite" 'apostrophe'""", assertNotNull(article).summary)
    }

    @Test
    fun anEscapedAmpersandDoesNotReintroduceATag() {
        // `&amp;lt;` must remain a literal `&lt;`: decoding `&amp;` first
        // would reintroduce the tag just neutralized.
        val article = item.copy(summary = ContentDto("&amp;lt;script&amp;gt;")).toArticleOrNull()

        assertEquals("&lt;script&gt;", assertNotNull(article).summary)
    }

    // ----- Remaining fields --------------------------------------------------

    @Test
    fun aBlankAuthorBecomesNull() {
        // A whitespace string would display an empty author line, more
        // confusing than no line at all.
        val blank = item.copy(author = "   ").toArticleOrNull()
        val empty = item.copy(author = "").toArticleOrNull()

        assertNull(assertNotNull(blank).author)
        assertNull(assertNotNull(empty).author)
    }

    @Test
    fun theFeedIsIdentifiedByItsStreamIdAndNamedByItsTitle() {
        // In a mixed feed the source is what makes the article intelligible
        // (SPECS.md §4.3).
        val article = item.copy(origin = OriginDto(streamId = "feed/12", title = "Exemple")).toArticleOrNull()

        assertEquals("feed/12", assertNotNull(article).feed.id)
        assertEquals("Exemple", article.feed.title)
    }

    @Test
    fun aMissingSummaryFallsBackToTheContentField() {
        // `summary` is the norm for this endpoint, but an article with
        // content and no excerpt must not display empty.
        val article = item.copy(summary = null, content = ContentDto("<p>Le corps entier.</p>")).toArticleOrNull()

        assertEquals("Le corps entier.", assertNotNull(article).summary)
    }

    @Test
    fun anArticleWithNeitherSummaryNorContentHasAnEmptyExcerpt() {
        val article = item.copy(summary = null, content = null).toArticleOrNull()

        assertEquals("", assertNotNull(article).summary)
    }

    @Test
    fun thePublicationDateIsCarriedUnchangedInSeconds() {
        // Three time units coexist in the same JSON object: only `published`
        // is in seconds.
        val article = item.copy(published = 1_699_999_000L).toArticleOrNull()

        assertEquals(1_699_999_000L, assertNotNull(article).publishedAtEpochSeconds)
    }
}
