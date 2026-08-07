package fr.vbrosseau.freshrssdiscover.data.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le JSON employé ici reproduit ce que produit `FreshRSS_Entry::toGReader` en
 * mode `compat` — le seul mode que `stream/contents` expose.
 *
 * Il est **littéral** et non simplifié : c'est ce qui permet de constater que
 * la désérialisation encaisse les champs absents, les unités hétérogènes et les
 * clés inconnues, plutôt que de vérifier qu'elle lit un JSON qu'on aurait
 * façonné pour elle.
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
        // Il n'existe aucun champ booléen. Chercher `isRead` reviendrait à
        // lire « non lu » pour tous les articles.
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
        // `Entry::toGReader` ne les émet que s'ils existent, et leur présence
        // dépend du flux RSS source. Les exiger ferait échouer la lecture d'un
        // flux parfaitement normal.
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
        assertNull(item.origin.htmlUrl)
    }

    @Test
    fun anItemWithNoSummaryAtAllIsStillReadable() {
        val decoded = decode("""{"items":[{"id":"tag:x/1","published":1,"title":"T"}]}""")

        assertNull(decoded.items.single().summary)
        assertEquals("", decoded.items.single().origin.streamId)
    }

    @Test
    fun anEnclosureTypeMayBeTheBareWordImage() {
        // Quand le flux source ne précise rien, FreshRSS se rabat sur `image`
        // plutôt que sur un type MIME complet. Comparer à « image/… » raterait
        // ces illustrations.
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

    @Test
    fun theSubscriptionListIsReadIncludingItsPrefixedKey() {
        // `frss:priority` porte un préfixe qui n'est pas un identifiant Kotlin
        // valide : sans @SerialName, il serait silencieusement ignoré.
        val decoded = FreshRssJson.decodeFromString(
            SubscriptionListDto.serializer(),
            """
            {"subscriptions":[{
              "id":"feed/12","title":"Exemple","url":"https://exemple.org/rss",
              "htmlUrl":"https://exemple.org/","iconUrl":"https://serveur/f.php",
              "categories":[{"id":"user/-/label/Tech","label":"Tech"}],
              "frss:priority":"main_stream"
            }]}
            """.trimIndent(),
        )

        assertEquals("feed/12", decoded.subscriptions.single().id)
        assertEquals("main_stream", decoded.subscriptions.single().priority)
    }
}
