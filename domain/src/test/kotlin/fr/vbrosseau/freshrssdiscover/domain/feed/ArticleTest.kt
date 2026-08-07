package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ArticleTest {
    @Test
    fun anArticleWithoutAUsableLinkIsRepresentable() {
        // Flux mal formé, contenu purement local : SPECS.md §4.7 demande de le
        // rendre non cliquable plutôt que d'ouvrir une page vide.
        assertNull(article(url = null).url)
    }

    @Test
    fun anArticleWithoutAnIllustrationIsRepresentable() {
        // Aucune image de remplacement : SPECS.md §4.3 demande qu'un article
        // sans illustration reste lisible, pas qu'il affiche un cadre vide.
        assertNull(article(imageUrl = null).imageUrl)
    }

    @Test
    fun theSourceFeedTravelsWithTheArticle() {
        // Dans un flux mélangé, la source est ce qui rend l'article
        // intelligible. La résoudre à l'affichage la ferait apparaître après
        // coup.
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
        // L'API ne fournit pas de champ « lu » : il se déduit de `categories`.
        // Le domaine, lui, ne connaît qu'un booléen.
        assertEquals(false, article().isRead)
        assertEquals(true, article(isRead = true).isRead)
    }
}
