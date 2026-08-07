package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val ARTICLE_IDS = listOf(1L, 2L, 3L)

/**
 * Les fractions sont comparées à une tolérance près : 0,4 n'est pas
 * représentable exactement en binaire, et `1 - 0,4` tombe à un demi-ulp de
 * 0,6 — exactement la raison pour laquelle SPECS.md §4.5 rend ses seuils
 * inclusifs.
 */
private const val TOLERANCE = 1e-4f

class SwipeVisibilityTest {

    @Test
    fun aSettledArticleFillsTheWholeScreen() {
        // C'est la propriété qui fait basculer la règle de SPECS.md §4.5 : le
        // seuil de surface est satisfait d'emblée, la durée décide seule.
        val visibility = pagerVisibility(ARTICLE_IDS, currentPage = 1, currentPageOffsetFraction = 0f)

        assertEquals(setOf(ArticleId(2L)), visibility.keys)
        assertEquals(1f, visibility.getValue(ArticleId(2L)), TOLERANCE)
    }

    @Test
    fun aSwipeInProgressSharesTheScreenBetweenTwoArticles() {
        val visibility = pagerVisibility(ARTICLE_IDS, currentPage = 0, currentPageOffsetFraction = 0.25f)

        assertEquals(0.75f, visibility.getValue(ArticleId(1L)), TOLERANCE)
        assertEquals(0.25f, visibility.getValue(ArticleId(2L)), TOLERANCE)
    }

    @Test
    fun swipingBackwardsExposesThePreviousArticle() {
        val visibility = pagerVisibility(ARTICLE_IDS, currentPage = 1, currentPageOffsetFraction = -0.4f)

        assertEquals(0.6f, visibility.getValue(ArticleId(2L)), TOLERANCE)
        assertEquals(0.4f, visibility.getValue(ArticleId(1L)), TOLERANCE)
    }

    @Test
    fun neitherArticleIsFullyVisibleHalfwayThroughAGesture() {
        // Un balayage lent ne doit marquer ni l'un ni l'autre : à mi-course,
        // aucun des deux n'atteint les 60 % de SPECS.md §4.5.
        val visibility = pagerVisibility(ARTICLE_IDS, currentPage = 0, currentPageOffsetFraction = 0.5f)

        assertTrue(visibility.values.all { it < 0.6f })
    }

    @Test
    fun theFirstArticleHasNoPredecessorToShareWith() {
        val visibility = pagerVisibility(ARTICLE_IDS, currentPage = 0, currentPageOffsetFraction = -0.3f)

        assertEquals(setOf(ArticleId(1L)), visibility.keys)
        assertEquals(0.7f, visibility.getValue(ArticleId(1L)), TOLERANCE)
    }

    @Test
    fun theEndOfFeedPageIsNotAnArticle() {
        // Le rang au-delà du dernier article est la page de fin : rien n'y est
        // chronométré, sans quoi on marquerait comme lu un message.
        val visibility = pagerVisibility(ARTICLE_IDS, currentPage = 3, currentPageOffsetFraction = 0f)

        assertTrue(visibility.isEmpty())
    }

    @Test
    fun theLastArticleSharesNothingWithTheEndOfFeedPage() {
        val visibility = pagerVisibility(ARTICLE_IDS, currentPage = 2, currentPageOffsetFraction = 0.3f)

        assertEquals(setOf(ArticleId(3L)), visibility.keys)
        assertEquals(0.7f, visibility.getValue(ArticleId(3L)), TOLERANCE)
    }

    @Test
    fun anEmptyFeedObservesNothing() {
        val visibility = pagerVisibility(emptyList(), currentPage = 0, currentPageOffsetFraction = 0f)

        assertTrue(visibility.isEmpty())
    }
}
