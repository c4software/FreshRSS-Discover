package fr.vbrosseau.freshrssdiscover.data.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The JSON here reproduces what `FreshRSS_Entry::toGReader` produces in
 * `compat` mode, the only mode `stream/contents` exposes.
 *
 * Literal, not simplified: this verifies deserialization handles absent
 * fields, mixed units, and unknown keys, rather than reading JSON shaped for
 * it.
 */
class StreamContentsDtoTest {
    private fun decode(json: String) = FreshRssJson.decodeFromString(StreamContentsDto.serializer(), json)

    private val completeResponse = """
        {
          "id": "user/-/state/com.google/reading-list",
          "updated": 1700000000,
          "items": [
            {
              "id": "tag:google.com,2005:reader/item/00000000000b0b1f",
              "crawlTimeMsec": "1700000000000",
              "timestampUsec": "1700000000000000",
              "published": 1699999000,
              "title": "Un titre d'article",
              "canonical": [{ "href": "https://exemple.org/article" }],
              "alternate": [{ "href": "https://exemple.org/article" }],
              "categories": [
                "user/-/state/com.google/reading-list",
                "user/-/label/Tech",
                "user/-/state/org.freshrss/main",
                "user/-/state/com.google/read"
              ],
              "origin": {
                "streamId": "feed/12",
                "htmlUrl": "https://exemple.org/",
                "title": "Exemple"
              },
              "summary": { "content": "<p>Un extrait.</p>" },
              "author": "Alice",
              "enclosure": [
                { "href": "https://exemple.org/image.jpg", "type": "image/jpeg", "length": 12345 }
              ]
            }
          ],
          "continuation": "45219"
        }
    """.trimIndent()

    @Test
    fun aCompleteResponseIsReadEntirely() {
        val decoded = decode(completeResponse)
        val item = decoded.items.single()

        assertEquals("45219", decoded.continuation)
        assertEquals("tag:google.com,2005:reader/item/00000000000b0b1f", item.id)
        assertEquals("Un titre d'article", item.title)
        assertEquals(1_699_999_000L, item.published)
        assertEquals("https://exemple.org/article", item.canonical.single().href)
        assertEquals("<p>Un extrait.</p>", item.summary?.content)
        assertEquals("Alice", item.author)
        assertEquals("feed/12", item.origin.streamId)
        assertEquals("Exemple", item.origin.title)
        assertEquals("image/jpeg", item.enclosure.single().type)
    }

    @Test
    fun theReadStateIsCarriedByCategoriesAndNothingElse() {
        // There is no boolean field. Looking for `isRead` would read every
        // article as unread.
        val item = decode(completeResponse).items.single()

        assertTrue("user/-/state/com.google/read" in item.categories)
    }

    @Test
    fun anAbsentContinuationMeansTheEndOfTheFeed() {
        val decoded = decode("""{"id":"user/-/state/com.google/reading-list","updated":1,"items":[]}""")

        assertNull(decoded.continuation)
        assertTrue(decoded.items.isEmpty())
    }

    @Test
    fun anItemWithoutAuthorEnclosureOrHtmlUrlIsPerfectlyNormal() {
        // `Entry::toGReader` only emits them when they exist, and their
        // presence depends on the source RSS feed. Requiring them would fail
        // reading a perfectly normal feed.
        val decoded = decode(
            """
            {
              "items": [{
                "id": "tag:google.com,2005:reader/item/0000000000000001",
                "published": 1,
                "title": "Minimal",
                "canonical": [{"href": "https://exemple.org/a"}],
                "categories": ["user/-/state/com.google/reading-list"],
                "origin": {"streamId": "feed/1", "title": "Flux"},
                "summary": {"content": ""}
              }]
            }
            """.trimIndent(),
        )
        val item = decoded.items.single()

        assertNull(item.author)
        assertTrue(item.enclosure.isEmpty())
    }

    @Test
    fun anItemWithNoSummaryAtAllIsStillReadable() {
        val decoded = decode("""{"items":[{"id":"tag:x/1","published":1,"title":"T"}]}""")

        assertNull(decoded.items.single().summary)
        assertEquals("", decoded.items.single().origin.streamId)
    }

    @Test
    fun anEnclosureTypeMayBeTheBareWordImage() {
        // When the source feed specifies nothing, FreshRSS falls back to
        // `image` rather than a full MIME type. Comparing against "image/..."
        // would miss those illustrations.
        val decoded = decode(
            """{"items":[{"id":"tag:x/1","enclosure":[{"href":"https://exemple.org/i.png","type":"image"}]}]}""",
        )

        assertEquals("image", decoded.items.single().enclosure.single().type)
    }

    @Test
    fun unknownFieldsAddedByAFutureVersionAreIgnored() {
        val decoded = decode("""{"items":[],"champTotalementNouveau":{"imbriqué":[1,2,3]}}""")

        assertTrue(decoded.items.isEmpty())
    }
}
