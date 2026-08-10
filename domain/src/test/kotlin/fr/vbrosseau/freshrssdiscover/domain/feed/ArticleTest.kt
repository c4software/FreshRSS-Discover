package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ArticleTest {
    @Test
    fun anArticleWithoutAUsableLinkIsRepresentable() {
        // Malformed feed or purely local content: SPECS.md §4.7 requires
        // making it non-clickable rather than opening an empty page.
        assertNull(article(url = null).url)
    }

    @Test
    fun anArticleWithoutAnIllustrationIsRepresentable() {
        // No placeholder image: SPECS.md §4.3 requires an article without an
        // illustration to stay readable, not to display an empty frame.
        assertNull(article(imageUrl = null).imageUrl)
    }

    @Test
    fun theSourceFeedTravelsWithTheArticle() {
        // In a shuffled feed, the source is what makes the article
        // intelligible. Resolving it at display time would make it appear
        // after the fact.
        val subject = article(feed = feedRef(id = "feed/42", title = "Le Monde"))

        assertEquals("Le Monde", subject.feed.title)
        assertEquals("feed/42", subject.feed.id)
    }

    @Test
    fun identifiersAreDecimalAndCompareByValue() {
        assertEquals(ArticleId(724_255L), ArticleId(724_255L))
        assertNotEquals(ArticleId(724_255L), ArticleId(724_256L))
        assertEquals(724_255L, ArticleId(724_255L).value)
    }

    @Test
    fun articlesCompareByValue() {
        assertEquals(article(id = 1), article(id = 1))
        assertEquals(article(id = 1).hashCode(), article(id = 1).hashCode())
        assertNotEquals(article(id = 1), article(id = 2))
    }

    @Test
    fun readStateIsCarriedByTheArticleItself() {
        // The API provides no "read" field: it is derived from `categories`.
        // The domain only knows a boolean.
        assertEquals(false, article().isRead)
        assertEquals(true, article(isRead = true).isRead)
    }
}
