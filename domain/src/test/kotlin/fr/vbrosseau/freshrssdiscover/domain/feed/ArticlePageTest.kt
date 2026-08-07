package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticlePageTest {
    @Test
    fun anAbsentCursorMeansTheFeedIsExhausted() {
        // C'est le seul signal de fin : l'API ne renvoie aucun compteur total.
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
        // Le serveur n'émet `continuation` que s'il *peut* rester quelque
        // chose. Une page pleine sans curseur n'est donc pas une anomalie, et
        // la traiter comme telle ferait redemander indéfiniment.
        val page = ArticlePage(articles = List(40) { article(id = it.toLong()) }, nextCursor = null)

        assertEquals(40, page.articles.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun anEmptyPageWithACursorStillHasMore() {
        // Cas réel : tous les articles de la page ont été filtrés côté serveur.
        // S'arrêter là masquerait les articles suivants.
        val page = ArticlePage(articles = emptyList(), nextCursor = PageCursor("45219"))

        assertTrue(page.hasMore)
    }

    @Test
    fun aCursorIsNotAPlainStringByAccident() {
        // Le type dédié empêche de fabriquer un curseur depuis n'importe quoi :
        // un curseur invalide est silencieusement ramené au début du flux par
        // le serveur, ce qui répéterait la première page sans jamais échouer.
        assertEquals("45219", PageCursor("45219").value)
        assertEquals(PageCursor("45219"), PageCursor("45219"))
    }
}
