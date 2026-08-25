package fr.vbrosseau.freshrssdiscover.presentation.immersive

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val ARTICLE_IDS = listOf(1L, 2L, 3L)

/**
 * Fractions are compared with a tolerance: 0.4 is not exactly representable in
 * binary, and `1 - 0.4` lands half an ulp from 0.6, exactly why SPECS.md §4.5
 * makes its thresholds inclusive.
 */
private const val TOLERANCE = 1e-4f

class ImmersiveVisibilityTest {

    @Test
    fun aSettledArticleFillsTheWholeScreen() {
        // The property that switches the SPECS.md §4.5 rule: the surface
        // threshold is met from the start, so duration alone decides.
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
        // A slow swipe must mark neither: halfway through, neither reaches the
        // 60% of SPECS.md §4.5.
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
        // The index beyond the last article is the end page: nothing is timed
        // there, or a message would be marked as read.
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
