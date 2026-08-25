package fr.vbrosseau.freshrssdiscover.presentation.immersive

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A 1080-pixel-wide page: the author's Pixel. */
private const val PAGE_W = 1080

class BackdropFitTest {

    @Test
    fun anArticleAlwaysGetsTheSameLayout() {
        // The look is part of the article: it must not change between two
        // sessions, nor between two recompositions.
        assertEquals(backdropFit(42L, 1920, PAGE_W), backdropFit(42L, 1920, PAGE_W))
    }

    @Test
    fun consecutiveArticlesGetDifferentLayouts() {
        // The point of the draw (author's ruling, 2026-08-25): variety.
        val looks = (1L..3L).map { backdropFit(it, 1920, PAGE_W) }.toSet()

        assertEquals(setOf(BackdropFit.Full, BackdropFit.Framed, BackdropFit.Tilted), looks)
    }

    @Test
    fun aNegativeIdStillDrawsALayoutRatherThanCrashing() {
        assertTrue(backdropFit(-7L, 1920, PAGE_W) != BackdropFit.Native)
    }

    @Test
    fun aPictureNarrowerThanThePageKeepsItsOwnSizeWhateverTheDraw() {
        // The List card's rule (SPECS.md §8, question 12).
        (1L..3L).forEach { id -> assertEquals(BackdropFit.Native, backdropFit(id, 300, PAGE_W), "article $id") }
    }

    @Test
    fun unknownSizesFillThePageRatherThanGuess() {
        assertEquals(BackdropFit.Full, backdropFit(2L, 0, PAGE_W))
        assertEquals(BackdropFit.Full, backdropFit(2L, 1920, 0))
    }

    @Test
    fun neighboursLeanOppositeWays() {
        assertNotEquals(tiltDegrees(1L), tiltDegrees(2L))
        assertEquals(-tiltDegrees(1L), tiltDegrees(2L))
    }
}
