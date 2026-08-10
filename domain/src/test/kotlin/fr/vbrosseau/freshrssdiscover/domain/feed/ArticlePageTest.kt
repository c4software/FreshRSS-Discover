package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticlePageTest {
    @Test
    fun anAbsentCursorMeansTheFeedIsExhausted() {
        // The only end-of-feed signal: the API returns no total count.
        val page = ArticlePage(articles = listOf(article()), nextCursor = null)

        assertFalse(page.hasMore)
    }

    @Test
    fun aCursorMeansMoreArticlesCanBeAsked() {
        val page = ArticlePage(articles = listOf(article()), nextCursor = PageCursor("45219"))

        assertTrue(page.hasMore)
    }

    @Test
    fun aFullPageWithoutACursorIsALegitimateEnd() {
        // The server only emits `continuation` when something may remain. A
        // full page without a cursor is therefore not an anomaly, and
        // treating it as one would cause endless re-requests.
        val page = ArticlePage(articles = List(40) { article(id = it.toLong()) }, nextCursor = null)

        assertEquals(40, page.articles.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun anEmptyPageWithACursorStillHasMore() {
        // Real case: every article on the page was filtered server-side.
        // Stopping there would hide the following articles.
        val page = ArticlePage(articles = emptyList(), nextCursor = PageCursor("45219"))

        assertTrue(page.hasMore)
    }

    @Test
    fun aCursorIsNotAPlainStringByAccident() {
        // The dedicated type prevents building a cursor from arbitrary input:
        // the server silently resets an invalid cursor to the start of the
        // feed, repeating the first page without ever failing.
        assertEquals("45219", PageCursor("45219").value)
        assertEquals(PageCursor("45219"), PageCursor("45219"))
    }
}
