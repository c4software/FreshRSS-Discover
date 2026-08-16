package fr.vbrosseau.freshrssdiscover.domain.recap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The parser, tested on what a streaming small model actually produces:
 * complete lines, a truncated tail, drifting numbering, and prose that
 * ignores the format altogether.
 */
class RecapLinesTest {
    @Test
    fun numberedLinesComeBackWithTheirIndex() {
        val lines = parseRecapLines("1. Premier résumé.\n2. Second résumé.")

        assertEquals(
            listOf(
                RecapLine(index = 1, text = "Premier résumé."),
                RecapLine(index = 2, text = "Second résumé."),
            ),
            lines,
        )
    }

    @Test
    fun aParenthesisNumberIsToleratedTheModelDrifts() {
        assertEquals(
            listOf(RecapLine(index = 3, text = "Un résumé.")),
            parseRecapLines("3) Un résumé."),
        )
    }

    @Test
    fun aStreamingTailStillParsesLineByLine() {
        val lines = parseRecapLines("1. Complet.\n2. Encore en cours de généra")

        assertEquals(2, lines.size)
        assertEquals("Encore en cours de généra", lines[1].text)
    }

    @Test
    fun proseWithoutNumbersParsesToNothing() {
        assertTrue(parseRecapLines("Voici un digest en prose, sans numéros.").isEmpty())
    }

    @Test
    fun blankAndUnnumberedLinesAreDropped() {
        val lines = parseRecapLines("Intro polie.\n\n1. Le seul résumé.\n")

        assertEquals(listOf(RecapLine(index = 1, text = "Le seul résumé.")), lines)
    }

    @Test
    fun anAbsurdNumberIsDroppedRatherThanCrashing() {
        assertTrue(parseRecapLines("99999999999999999999. Débordement.").isEmpty())
    }
}
