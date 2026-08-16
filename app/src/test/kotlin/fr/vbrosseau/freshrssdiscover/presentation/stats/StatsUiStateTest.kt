package fr.vbrosseau.freshrssdiscover.presentation.stats

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsUiStateTest {
    @Test
    fun anEmptyHistogramHasNoDataAndFlatBars() {
        val state = statsUiStateOf(ReadingHistogram.Empty)

        assertFalse(state.hasData)
        assertNull(state.dominantHour)
        assertEquals(24, state.bars.size)
        assertTrue(state.bars.all { it.fraction == 0f })
    }

    @Test
    fun theTallestBarFillsTheWholeHeight() {
        val histogram =
            ReadingHistogram.Empty
                .record(day = 1, hour = 8)
                .record(day = 1, hour = 21)
                .record(day = 2, hour = 21)

        val state = statsUiStateOf(histogram)

        assertEquals(1f, state.bars[21].fraction)
        assertTrue(state.bars[8].fraction in 0f..1f)
        assertTrue(state.bars[8].fraction > 0f)
    }

    @Test
    fun theDominantHourIsTheHistogramsOwnDecision() {
        // The screen shows the reminder's reasoning (SPECS.md §4.9): it must
        // publish the same decision, threshold included — a histogram with
        // data but below the sufficiency threshold designates nothing.
        val insufficient = ReadingHistogram.Empty.record(day = 1, hour = 21)

        val state = statsUiStateOf(insufficient)

        assertTrue(state.hasData)
        assertNull(state.dominantHour)
    }

    @Test
    fun aSufficientHistogramDesignatesItsDominantHour() {
        val histogram =
            ReadingHistogram.Empty
                .record(day = 1, hour = 21)
                .record(day = 1, hour = 20)
                .record(day = 1, hour = 22)
                .record(day = 2, hour = 21)

        assertEquals(21, statsUiStateOf(histogram).dominantHour)
    }
}
