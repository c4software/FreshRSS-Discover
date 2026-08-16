package fr.vbrosseau.freshrssdiscover.presentation

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import fr.vbrosseau.freshrssdiscover.presentation.stats.StatsScreen
import fr.vbrosseau.freshrssdiscover.presentation.stats.statsUiStateOf
import org.junit.Test

/**
 * Visual references for the reading statistics screen (SPECS.md §6).
 *
 * The chart is the only bar drawing in the application: nothing else shows a
 * primary-colored mark next to `surfaceVariant` ones, and a contrast defect
 * between the two — the dominant bar melting into its neighbours in dark
 * theme — would break no textual assertion.
 */
class StatsScreenshotTest : ScreenshotTest() {

    /** Two reading habits: a dominant evening, a lighter morning. */
    @Test
    fun statsScreenWithADominantEvening() {
        // The evening must genuinely dominate: with every hour recorded every
        // day the bins tie, and the tie-break silently hands the crown to the
        // morning — the first capture of this screen showed exactly that.
        var histogram = ReadingHistogram.Empty
        for (day in 1L..10L) {
            histogram = histogram.record(day, hour = 21).record(day, hour = 22)
            if (day % 2L == 0L) histogram = histogram.record(day, hour = 8)
        }

        capture("stats-heures-de-lecture") {
            StatsScreen(uiState = statsUiStateOf(histogram))
        }
    }

    @Test
    fun statsScreenStillLearning() {
        capture("stats-apprentissage") {
            StatsScreen(uiState = statsUiStateOf(ReadingHistogram.Empty.record(day = 1, hour = 21)))
        }
    }

    @Test
    fun statsScreenEmpty() {
        capture("stats-vide") {
            StatsScreen(uiState = statsUiStateOf(ReadingHistogram.Empty))
        }
    }
}
