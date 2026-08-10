package fr.vbrosseau.freshrssdiscover.presentation.feed

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rule deciding whether the blurred background applies (SPECS.md §4.3).
 *
 * The important cases are the three where nothing must happen: image wide
 * enough, image exactly at slot width, and unknown size. The last matters most:
 * blurring on a guess would degrade correct images.
 */
class IllustrationFitTest {
    @Test
    fun anImageNarrowerThanItsSlotWouldBeUpscaled() {
        assertTrue(needsUpscaling(sourceWidthPx = 200, slotWidthPx = 1080))
    }

    @Test
    fun anImageWiderThanItsSlotIsLeftAlone() {
        // A wider image is cropped, never stretched; nothing to correct.
        assertFalse(needsUpscaling(sourceWidthPx = 1920, slotWidthPx = 1080))
    }

    @Test
    fun anImageExactlyAtItsSlotWidthIsLeftAlone() {
        // The bound is strict: otherwise the effect would trigger on a
        // perfectly sharp image.
        assertFalse(needsUpscaling(sourceWidthPx = 1080, slotWidthPx = 1080))
    }

    @Test
    fun anUnknownSourceSizeDecidesNothing() {
        // The image is still loading, or Coil has not reported its size:
        // never blur on a guess.
        assertFalse(needsUpscaling(sourceWidthPx = 0, slotWidthPx = 1080))
        assertFalse(needsUpscaling(sourceWidthPx = -1, slotWidthPx = 1080))
    }

    @Test
    fun anUnmeasuredSlotDecidesNothing() {
        // First composition: the slot width is not measured yet. Deciding now
        // would make the background flicker on the next measurement.
        assertFalse(needsUpscaling(sourceWidthPx = 200, slotWidthPx = 0))
    }

    @Test
    fun aTallButWideEnoughImageIsLeftAlone() {
        // Height does not participate: treating it would blur sharp banners.
        assertFalse(needsUpscaling(sourceWidthPx = 1200, slotWidthPx = 1080))
    }
}
