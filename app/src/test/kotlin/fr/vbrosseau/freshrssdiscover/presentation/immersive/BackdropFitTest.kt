package fr.vbrosseau.freshrssdiscover.presentation.immersive

import org.junit.Test
import kotlin.test.assertEquals

/** A 1080 × 2340 page: the author's Pixel. */
private const val PAGE_W = 1080
private const val PAGE_H = 2340

class BackdropFitTest {

    @Test
    fun aSixteenByNineBannerGoesFullScreen() {
        // The feed's ordinary picture (author's ruling, 2026-08-25): 1080p
        // and 720p banners both cover the page without going soft.
        assertEquals(BackdropFit.Full, backdropFit(1920, 1080, PAGE_W, PAGE_H))
        assertEquals(BackdropFit.Full, backdropFit(1280, 720, PAGE_W, PAGE_H))
    }

    @Test
    fun aThumbnailTooSmallToCoverThePageIsFramed() {
        // At 480 pixels tall the crop would enlarge it almost five times.
        assertEquals(BackdropFit.Framed, backdropFit(1100, 480, PAGE_W, PAGE_H))
    }

    @Test
    fun aPictureNarrowerThanThePageKeepsItsOwnSize() {
        // The List card's rule (SPECS.md §8, question 12), whatever the ratio.
        assertEquals(BackdropFit.Native, backdropFit(300, 900, PAGE_W, PAGE_H))
        assertEquals(BackdropFit.Native, backdropFit(600, 200, PAGE_W, PAGE_H))
    }

    @Test
    fun unknownSizesFillThePageRatherThanGuess() {
        assertEquals(BackdropFit.Full, backdropFit(0, 0, PAGE_W, PAGE_H))
        assertEquals(BackdropFit.Full, backdropFit(1920, 1080, 0, 0))
    }
}
