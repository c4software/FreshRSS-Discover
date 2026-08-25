package fr.vbrosseau.freshrssdiscover.presentation.immersive

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comparisons use a tolerance, as in `ImmersiveVisibilityTest`: `-0.0` and
 * `0.0` are distinguished by `Float.equals` while no rendering does.
 */
private const val TOLERANCE = 1e-4f

/**
 * Page motion, tested without rendering.
 *
 * These tests protect properties no screenshot shows: at no instant of the
 * gesture may a page vanish, shrink away, or have its backdrop overtake it.
 */
class ImmersivePageTransformTest {

    @Test
    fun aSettledPageIsLeftExactlyAsItIs() {
        val transform = immersivePageTransform(0f)

        assertEquals(1f, transform.scale, TOLERANCE)
        assertEquals(1f, transform.alpha, TOLERANCE)
        assertEquals(0f, transform.backdropTranslationYFraction, TOLERANCE)
    }

    @Test
    fun bothDirectionsMoveThePageTheSameWay() {
        // Going forward and coming back are one gesture read backwards: a
        // page leaving upward and one leaving downward must look alike.
        val up = immersivePageTransform(0.4f)
        val down = immersivePageTransform(-0.4f)

        assertEquals(up.scale, down.scale, TOLERANCE)
        assertEquals(up.alpha, down.alpha, TOLERANCE)
    }

    @Test
    fun aPageShrinksAndFadesSteadilyAsItLeaves() {
        val early = immersivePageTransform(0.2f)
        val late = immersivePageTransform(0.8f)

        assertTrue(early.scale > late.scale, "${early.scale} > ${late.scale}")
        assertTrue(early.alpha > late.alpha, "${early.alpha} > ${late.alpha}")
    }

    @Test
    fun aPageNeverBecomesInvisibleWhileStillOnScreen() {
        // The neighbour is partly on screen while this page is still mostly
        // there: a page invisible at the edge would reveal a hole.
        var offset = -1f
        while (offset <= 1f) {
            assertTrue(immersivePageTransform(offset).alpha >= EXPECTED_MIN_ALPHA, "à $offset")
            offset += 0.05f
        }
    }

    @Test
    fun theBackdropLagsBehindThePageInBothDirections() {
        // The page leaves upward (positive offset): the backdrop is pushed
        // back down, and the other way round. Same sign as the offset.
        assertTrue(immersivePageTransform(0.5f).backdropTranslationYFraction > 0f)
        assertTrue(immersivePageTransform(-0.5f).backdropTranslationYFraction < 0f)
    }

    @Test
    fun theBackdropNeverLagsByMoreThanAFractionOfThePage() {
        // Beyond a quarter, the top edge of the illustration would show
        // mid-gesture.
        assertTrue(immersivePageTransform(1f).backdropTranslationYFraction <= EXPECTED_MAX_PARALLAX)
    }

    @Test
    fun aPageBeyondTheScreenIsNotPushedFurtherThanTheEdgeValues() {
        // The pager sometimes composes beyond one page: without a bound, the
        // scale would keep shrinking and the alpha would go below zero.
        val far = immersivePageTransform(2.5f)
        val edge = immersivePageTransform(1f)

        assertEquals(edge.scale, far.scale, TOLERANCE)
        assertEquals(edge.alpha, far.alpha, TOLERANCE)
        assertEquals(edge.backdropTranslationYFraction, far.backdropTranslationYFraction, TOLERANCE)
        assertTrue(far.scale > 0f)
    }

    private companion object {
        /** The residual alpha declared by the module, restated here as an expectation. */
        const val EXPECTED_MIN_ALPHA = 0.55f

        /** The parallax share declared by the module, restated here as an expectation. */
        const val EXPECTED_MAX_PARALLAX = 0.25f
    }
}
