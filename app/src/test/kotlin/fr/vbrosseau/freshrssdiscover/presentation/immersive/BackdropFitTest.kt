package fr.vbrosseau.freshrssdiscover.presentation.immersive

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A 1080 × 2340 page: the author's Pixel. */
private const val PAGE_W = 1080
private const val PAGE_H = 2340

/** A picture that covers the page when cropped: no fallback in play. */
private fun fitOf(id: Long, w: Int = 1080, h: Int = 1920) = backdropFit(id, w, h, PAGE_W, PAGE_H)

class BackdropFitTest {

    @Test
    fun anArticleAlwaysGetsTheSameLayout() {
        // The look is part of the article: it must not change between two
        // sessions, nor between two recompositions.
        assertEquals(fitOf(42L), fitOf(42L))
    }

    @Test
    fun fiveConsecutiveArticlesShowEveryLookWithFullScreenFavoured() {
        // The point of the draw (author's rulings, 2026-08-25): variety,
        // with the full-screen picture as the norm, three times out of five.
        val looks = (1L..5L).map { fitOf(it) }

        assertEquals(3, looks.count { it == BackdropFit.Full })
        assertEquals(1, looks.count { it == BackdropFit.Framed })
        assertEquals(1, looks.count { it == BackdropFit.Tilted })
    }

    @Test
    fun twoConsecutiveArticlesNeverShareARelief() {
        // The two non-full looks are spread out, not grouped.
        (1L..10L).zipWithNext().forEach { (a, b) ->
            assertTrue(fitOf(a) == BackdropFit.Full || fitOf(b) == BackdropFit.Full, "articles $a et $b")
        }
    }

    @Test
    fun aSixteenByNineBannerGoesFullScreen() {
        // The feed's ordinary picture (author's ruling, 2026-08-25): 1080p
        // and 720p banners both cover the page without going soft.
        val fullDraw = (1L..5L).first { fitOf(it) == BackdropFit.Full }

        assertEquals(BackdropFit.Full, backdropFit(fullDraw, 1920, 1080, PAGE_W, PAGE_H))
        assertEquals(BackdropFit.Full, backdropFit(fullDraw, 1280, 720, PAGE_W, PAGE_H))
    }

    @Test
    fun aThumbnailTooSmallToCoverThePageIsFramedInsteadOfFullScreen() {
        // "Si la taille est suffisante": at 480 pixels tall the crop would
        // enlarge it almost five times.
        val fullDraw = (1L..5L).first { fitOf(it) == BackdropFit.Full }

        assertEquals(BackdropFit.Framed, backdropFit(fullDraw, 1100, 480, PAGE_W, PAGE_H))
    }

    @Test
    fun theSizeRuleOnlyTouchesTheFullScreenDraw() {
        val tiltedDraw = (1L..5L).first { fitOf(it) == BackdropFit.Tilted }

        assertEquals(BackdropFit.Tilted, backdropFit(tiltedDraw, 1100, 480, PAGE_W, PAGE_H))
    }

    @Test
    fun aNegativeIdStillDrawsALayoutRatherThanCrashing() {
        assertTrue(fitOf(-7L) != BackdropFit.Native)
    }

    @Test
    fun aPictureNarrowerThanThePageKeepsItsOwnSizeWhateverTheDraw() {
        // The List card's rule (SPECS.md §8, question 12).
        (1L..5L).forEach { id -> assertEquals(BackdropFit.Native, fitOf(id, w = 300, h = 900), "article $id") }
    }

    @Test
    fun unknownSizesFillThePageRatherThanGuess() {
        assertEquals(BackdropFit.Full, backdropFit(2L, 0, 0, PAGE_W, PAGE_H))
        assertEquals(BackdropFit.Full, backdropFit(2L, 1920, 1080, 0, 0))
    }

    @Test
    fun neighboursLeanOppositeWays() {
        assertNotEquals(tiltDegrees(1L), tiltDegrees(2L))
        assertEquals(-tiltDegrees(1L), tiltDegrees(2L))
    }
}
