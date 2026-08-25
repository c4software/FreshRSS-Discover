package fr.vbrosseau.freshrssdiscover.presentation.immersive

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comparisons use a tolerance, as in `ImmersiveVisibilityTest` and for a similar
 * reason: a null rotation comes out of the computation as `-0.0`, which
 * `Float.equals` distinguishes from `0.0` while no rendering does.
 */
private const val TOLERANCE = 1e-4f

/**
 * Card-stack geometry, tested without rendering.
 *
 * These tests protect properties no screenshot shows: at no instant of the
 * gesture may a card become invisible, upside down, or drawn in front of the
 * one that should cover it.
 */
class ImmersivePageTransformTest {

    @Test
    fun aSettledCardIsLeftExactlyAsItIs() {
        val transform = immersivePageTransform(0f)

        assertEquals(0f, transform.translationXFraction, TOLERANCE)
        assertEquals(0f, transform.rotationDegrees, TOLERANCE)
        assertEquals(1f, transform.scale, TOLERANCE)
        assertEquals(1f, transform.alpha, TOLERANCE)
    }

    @Test
    fun aCardOnItsWayOutLeansTowardsWhereItIsGoing() {
        // A positive offset means "leaving to the left": the top leans the
        // same way, hence a negative rotation.
        val transform = immersivePageTransform(0.5f)

        assertTrue(transform.rotationDegrees < 0f, "inclinaison ${transform.rotationDegrees}")
    }

    @Test
    fun aCardOnItsWayOutKeepsThePagerMovement() {
        // Nothing is added to the translation: the pager moves it off screen,
        // and the finger must feel it follow.
        assertEquals(0f, immersivePageTransform(0.5f).translationXFraction, TOLERANCE)
    }

    @Test
    fun theCardUnderneathStaysCentredInsteadOfSlidingIn() {
        // The pager placed it at +0.6 width; exactly the opposite is added.
        assertEquals(-0.6f, immersivePageTransform(-0.6f).translationXFraction, TOLERANCE)
    }

    @Test
    fun theCardUnderneathGrowsAsItIsRevealed() {
        val hidden = immersivePageTransform(-1f).scale
        val halfway = immersivePageTransform(-0.5f).scale
        val arrived = immersivePageTransform(0f).scale

        assertTrue(hidden < halfway && halfway < arrived, "$hidden < $halfway < $arrived")
        assertEquals(1f, arrived, TOLERANCE)
    }

    @Test
    fun theCardUnderneathNeverTiltsNorFades() {
        // It waits: tilting it would put two cards in motion, and the eye
        // would no longer know which one it is leaving.
        val transform = immersivePageTransform(-0.5f)

        assertEquals(0f, transform.rotationDegrees, TOLERANCE)
        assertEquals(1f, transform.alpha, TOLERANCE)
    }

    @Test
    fun theFlyingCardIsAlwaysDrawnOverTheDeck() {
        // True in both directions: going back, the previous card returns from
        // the left with a positive offset, and it must land on the deck rather
        // than slide underneath.
        assertTrue(immersivePageTransform(0.4f).drawOrder > immersivePageTransform(-0.6f).drawOrder)
        assertTrue(immersivePageTransform(0.6f).drawOrder > immersivePageTransform(-0.4f).drawOrder)
    }

    @Test
    fun aCardNeverBecomesInvisibleWhileStillOnScreen() {
        // A card that fades out completely before leaving the frame dissolves
        // in place, while the gesture says it is being set aside.
        var offset = 0f
        while (offset <= 1f) {
            assertTrue(immersivePageTransform(offset).alpha >= EXPECTED_MIN_ALPHA, "à $offset")
            offset += 0.05f
        }
    }

    @Test
    fun aCardFadesSteadilyAsItLeaves() {
        val early = immersivePageTransform(0.2f).alpha
        val late = immersivePageTransform(0.8f).alpha

        assertTrue(early > late, "$early > $late")
    }

    @Test
    fun aCardBeyondTheScreenIsNotPushedFurtherThanTheEdgeValues() {
        // The pager sometimes composes beyond one page: without a bound, the
        // rotation would keep growing and the alpha would go below zero.
        val far = immersivePageTransform(2.5f)

        assertEquals(immersivePageTransform(1f).rotationDegrees, far.rotationDegrees, TOLERANCE)
        assertEquals(immersivePageTransform(1f).alpha, far.alpha, TOLERANCE)
    }

    @Test
    fun aDeckCardFarBehindKeepsTheSmallestScaleRatherThanShrinkingAway() {
        val far = immersivePageTransform(-3f)

        assertEquals(immersivePageTransform(-1f).scale, far.scale, TOLERANCE)
        assertTrue(far.scale > 0f)
    }

    private companion object {
        /** The residual alpha declared by the module, restated here as an expectation. */
        const val EXPECTED_MIN_ALPHA = 0.4f
    }
}
