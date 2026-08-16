package fr.vbrosseau.freshrssdiscover.domain.recap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The brief parser, tested on what a streaming small model actually
 * produces: markers mid-prose, connective text between them, a truncated
 * marker at the tail, and prose that ignores the format altogether.
 */
class RecapSegmentTest {
    @Test
    fun aMarkerBindsThePrecedingProseToItsArticle() {
        val segments = parseRecapBrief("GNOME ouvre sa bêta [1]. Le Tensor G6 progresse [2].")

        assertEquals(
            listOf(
                RecapSegment(text = "GNOME ouvre sa bêta", articleIndex = 1),
                RecapSegment(text = ". Le Tensor G6 progresse", articleIndex = 2),
                RecapSegment(text = ".", articleIndex = null),
            ),
            segments,
        )
    }

    @Test
    fun theSpaceBeforeAMarkerLeavesNoGapBehind() {
        assertEquals("Une phrase", parseRecapBrief("Une phrase [3].").first().text)
    }

    @Test
    fun consecutiveMarkersKeepOnlyTheFirstBinding() {
        val segments = parseRecapBrief("Deux sources racontent le même procès [2][4].")

        assertEquals(listOf(2), segments.mapNotNull { it.articleIndex })
        assertEquals("Deux sources racontent le même procès", segments.first().text)
    }

    @Test
    fun aHalfStreamedMarkerStaysHidden() {
        val segments = parseRecapBrief("Le début du brief [1]. La suite arrive [2")

        assertEquals(
            listOf(
                RecapSegment(text = "Le début du brief", articleIndex = 1),
                RecapSegment(text = ". La suite arrive", articleIndex = null),
            ),
            segments,
        )
    }

    @Test
    fun proseWithoutMarkersBecomesOneUnlinkedSegment() {
        val segments = parseRecapBrief("Un paragraphe sans le format demandé.")

        assertEquals(listOf(RecapSegment("Un paragraphe sans le format demandé.", null)), segments)
    }

    @Test
    fun aBlankAnswerParsesToNothing() {
        assertTrue(parseRecapBrief("  \n").isEmpty())
    }

    @Test
    fun anAbsurdNumberKeepsItsProseUnlinked() {
        val segments = parseRecapBrief("Débordement [99999999999999999999].")

        assertEquals(RecapSegment(text = "Débordement", articleIndex = null), segments.first())
    }
}
