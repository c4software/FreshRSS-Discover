package fr.vbrosseau.freshrssdiscover.presentation.swipe

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comparisons use a tolerance, as in `SwipeVisibilityTest` and for a similar
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
class SwipeCardTransformTest {

    @Test
    fun aSettledCardIsLeftExactlyAsItIs() {
        val transform = swipeCardTransform(0f)

        assertEquals(0f, transform.translationXFraction, TOLERANCE)
        assertEquals(0f, transform.rotationDegrees, TOLERANCE)
        assertEquals(1f, transform.scale, TOLERANCE)
        assertEquals(1f, transform.alpha, TOLERANCE)
    }

    @Test
    fun aCardOnItsWayOutLeansTowardsWhereItIsGoing() {
        // A positive offset means "leaving to the left": the top leans the
        // same way, hence a negative rotation.
        val transform = swipeCardTransform(0.5f)

        assertTrue(transform.rotationDegrees < 0f, "inclinaison ${transform.rotationDegrees}")
    }

    @Test
    fun aCardOnItsWayOutKeepsThePagerMovement() {
        // Nothing is added to the translation: the pager moves it off screen,
        // and the finger must feel it follow.
        assertEquals(0f, swipeCardTransform(0.5f).translationXFraction, TOLERANCE)
    }

    @Test
    fun theCardUnderneathStaysCentredInsteadOfSlidingIn() {
        // The pager placed it at +0.6 width; exactly the opposite is added.
        assertEquals(-0.6f, swipeCardTransform(-0.6f).translationXFraction, TOLERANCE)
    }

    @Test
    fun theCardUnderneathGrowsAsItIsRevealed() {
        val hidden = swipeCardTransform(-1f).scale
        val halfway = swipeCardTransform(-0.5f).scale
        val arrived = swipeCardTransform(0f).scale

        assertTrue(hidden < halfway && halfway < arrived, "$hidden < $halfway < $arrived")
        assertEquals(1f, arrived, TOLERANCE)
    }

    @Test
    fun theCardUnderneathNeverTiltsNorFades() {
        // It waits: tilting it would put two cards in motion, and the eye
        // would no longer know which one it is leaving.
        val transform = swipeCardTransform(-0.5f)

        assertEquals(0f, transform.rotationDegrees, TOLERANCE)
        assertEquals(1f, transform.alpha, TOLERANCE)
    }

    @Test
    fun theFlyingCardIsAlwaysDrawnOverTheDeck() {
        // True in both directions: going back, the previous card returns from
        // the left with a positive offset, and it must land on the deck rather
        // than slide underneath.
        assertTrue(swipeCardTransform(0.4f).drawOrder > swipeCardTransform(-0.6f).drawOrder)
        assertTrue(swipeCardTransform(0.6f).drawOrder > swipeCardTransform(-0.4f).drawOrder)
    }

    @Test
    fun aCardNeverBecomesInvisibleWhileStillOnScreen() {
        // A card that fades out completely before leaving the frame dissolves
        // in place, while the gesture says it is being set aside.
        var offset = 0f
        while (offset <= 1f) {
            assertTrue(swipeCardTransform(offset).alpha >= EXPECTED_MIN_ALPHA, "à $offset")
            offset += 0.05f
        }
    }

    @Test
    fun aCardFadesSteadilyAsItLeaves() {
        val early = swipeCardTransform(0.2f).alpha
        val late = swipeCardTransform(0.8f).alpha

        assertTrue(early > late, "$early > $late")
    }

    @Test
    fun aCardBeyondTheScreenIsNotPushedFurtherThanTheEdgeValues() {
        // The pager sometimes composes beyond one page: without a bound, the
        // rotation would keep growing and the alpha would go below zero.
        val far = swipeCardTransform(2.5f)

        assertEquals(swipeCardTransform(1f).rotationDegrees, far.rotationDegrees, TOLERANCE)
        assertEquals(swipeCardTransform(1f).alpha, far.alpha, TOLERANCE)
    }

    @Test
    fun aDeckCardFarBehindKeepsTheSmallestScaleRatherThanShrinkingAway() {
        val far = swipeCardTransform(-3f)

        assertEquals(swipeCardTransform(-1f).scale, far.scale, TOLERANCE)
        assertTrue(far.scale > 0f)
    }

    private companion object {
        /** The residual alpha declared by the module, restated here as an expectation. */
        const val EXPECTED_MIN_ALPHA = 0.4f
    }
}
