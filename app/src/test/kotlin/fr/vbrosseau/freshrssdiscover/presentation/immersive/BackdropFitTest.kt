package fr.vbrosseau.freshrssdiscover.presentation.immersive

import org.junit.Test
import kotlin.test.assertEquals

/** A 1080 × 2340 page: the author's Pixel. */
private const val PAGE_W = 1080
private const val PAGE_H = 2340

class BackdropFitTest {

    @Test
    fun aLandscapePhotographIsShownWholeAtThePageWidth() {
        // The device defect: cropped to portrait, a 16/9 banner lost two
        // thirds of its width and read as zoomed in.
        assertEquals(BackdropFit.FitWidth, backdropFit(1920, 1080, PAGE_W, PAGE_H))
    }

    @Test
    fun aPortraitPhotographAtLeastAsTallAsThePageIsCropped() {
        assertEquals(BackdropFit.Crop, backdropFit(1080, 2400, PAGE_W, PAGE_H))
        // Exactly the page's ratio: nothing to crop, cropping is harmless.
        assertEquals(BackdropFit.Crop, backdropFit(PAGE_W, PAGE_H, PAGE_W, PAGE_H))
    }

    @Test
    fun aPictureNarrowerThanThePageKeepsItsOwnSize() {
        // The List card's rule (SPECS.md §8, question 12), whatever the ratio.
        assertEquals(BackdropFit.Native, backdropFit(300, 900, PAGE_W, PAGE_H))
        assertEquals(BackdropFit.Native, backdropFit(600, 200, PAGE_W, PAGE_H))
    }

    @Test
    fun unknownSizesCropRatherThanGuess() {
        assertEquals(BackdropFit.Crop, backdropFit(0, 0, PAGE_W, PAGE_H))
        assertEquals(BackdropFit.Crop, backdropFit(1920, 1080, 0, 0))
    }
}
