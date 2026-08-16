package fr.vbrosseau.freshrssdiscover.domain.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The histogram, tested where a habit record can go wrong: the binge evening,
 * the day that decays, the clock that goes backwards, and the threshold below
 * which it must abstain.
 */
class ReadingHistogramTest {
    @Test
    fun theEmptyHistogramDesignatesNoHour() {
        assertNull(ReadingHistogram.Empty.dominantHour())
    }

    @Test
    fun anInsufficientHistogramDesignatesNoHour() {
        val two = ReadingHistogram.Empty.record(day = 1, hour = 21).record(day = 2, hour = 21)

        // Two sessions decayed once: below the threshold of three.
        assertTrue(two.totalWeight < HISTOGRAM_SUFFICIENT_WEIGHT)
        assertNull(two.dominantHour())
    }

    @Test
    fun threeFreshSessionsSuffice() {
        val three =
            ReadingHistogram.Empty
                .record(day = 1, hour = 21)
                .record(day = 1, hour = 22)
                .record(day = 1, hour = 20)

        assertTrue(three.isSufficient)
    }

    @Test
    fun theDensestHourWins() {
        val histogram =
            ReadingHistogram.Empty
                .record(day = 1, hour = 8)
                .record(day = 1, hour = 21)
                .record(day = 2, hour = 21)
                .record(day = 3, hour = 21)

        assertEquals(21, histogram.dominantHour())
    }

    @Test
    fun aTieBreaksOnTheEarliestHour() {
        val histogram =
            ReadingHistogram.Empty
                .record(day = 1, hour = 8)
                .record(day = 1, hour = 21)
                .record(day = 1, hour = 9)
                .record(day = 1, hour = 22)

        // 8, 9, 21 and 22 all weigh exactly 1.0 on the same day.
        assertEquals(8, histogram.dominantHour())
    }

    @Test
    fun aSecondReadingInTheSameHourOfTheSameDayRecordsNothing() {
        val once = ReadingHistogram.Empty.record(day = 1, hour = 21)

        // Same instance, not merely equal: the no-op must be free, because
        // marking articles read calls this on every batch of a scroll.
        assertSame(once, once.record(day = 1, hour = 21))
    }

    @Test
    fun aBingeEveningWeighsOneSessionPerHour() {
        val evening =
            ReadingHistogram.Empty
                .record(day = 1, hour = 21)
                .record(day = 1, hour = 21)
                .record(day = 1, hour = 22)
                .record(day = 1, hour = 22)

        assertEquals(1.0, evening.bins[21])
        assertEquals(1.0, evening.bins[22])
    }

    @Test
    fun theSameHourOnAnotherDayRecordsAgain() {
        val histogram = ReadingHistogram.Empty.record(day = 1, hour = 21).record(day = 2, hour = 21)

        assertEquals(HISTOGRAM_DAILY_DECAY + 1.0, histogram.bins[21])
    }

    @Test
    fun aNewDayDecaysEveryBin() {
        val histogram = ReadingHistogram.Empty.record(day = 1, hour = 8).record(day = 2, hour = 21)

        assertEquals(HISTOGRAM_DAILY_DECAY, histogram.bins[8])
        assertEquals(1.0, histogram.bins[21])
    }

    @Test
    fun severalElapsedDaysCompoundTheDecay() {
        val histogram = ReadingHistogram.Empty.record(day = 1, hour = 8).record(day = 4, hour = 8)

        val threeDays = HISTOGRAM_DAILY_DECAY * HISTOGRAM_DAILY_DECAY * HISTOGRAM_DAILY_DECAY
        assertEquals(threeDays + 1.0, histogram.bins[8], absoluteTolerance = 1e-12)
    }

    @Test
    fun aHabitThatMovedEndsUpWinning() {
        // Ten mornings, then five evenings: the evening must take over, which
        // is exactly what the decay is for.
        var histogram = ReadingHistogram.Empty
        (1L..10L).forEach { day -> histogram = histogram.record(day, hour = 8) }
        (11L..15L).forEach { day -> histogram = histogram.record(day, hour = 21) }

        assertEquals(21, histogram.dominantHour())
    }

    @Test
    fun decayMakesSufficiencyReversible() {
        val abandoned =
            ReadingHistogram.Empty
                .record(day = 1, hour = 21)
                .record(day = 2, hour = 21)
                .record(day = 3, hour = 21)
                .record(day = 60, hour = 8)

        // Weeks without reading: only the fresh session really remains, and
        // the histogram abstains rather than aiming at a stale habit.
        assertNull(abandoned.dominantHour())
    }

    @Test
    fun aClockYearsAheadDoesNotEraseTheFreshSession() {
        val histogram = ReadingHistogram.Empty.record(day = 1, hour = 21).record(day = 40_000, hour = 8)

        // The capped decay leaves the old bin negligible but the new session
        // whole; an uncapped power of 40 000 days would have been trouble in
        // itself (underflow to zero is fine, but the cap keeps it defined).
        assertEquals(1.0, histogram.bins[8])
        assertTrue(histogram.bins[21] < 0.05)
    }

    @Test
    fun aClockSetBackRecordsWithoutDecayingAndKeepsTheDay() {
        val histogram = ReadingHistogram.Empty.record(day = 10, hour = 21).record(day = 3, hour = 8)

        assertEquals(1.0, histogram.bins[21])
        assertEquals(1.0, histogram.bins[8])
        assertEquals(10L, histogram.lastDay)

        // The next real day decays by one day, not by the round trip.
        val next = histogram.record(day = 11, hour = 21)
        assertEquals(HISTOGRAM_DAILY_DECAY + 1.0, next.bins[21])
    }

    @Test
    fun anOutOfRangeHourIsRefused() {
        assertFailsWith<IllegalArgumentException> { ReadingHistogram.Empty.record(day = 1, hour = 24) }
        assertFailsWith<IllegalArgumentException> { ReadingHistogram.Empty.record(day = 1, hour = -1) }
    }

    @Test
    fun aMalformedStateIsRefusedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            ReadingHistogram(bins = List(23) { 0.0 }, lastDay = null, recordedHours = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            ReadingHistogram(bins = List(24) { -1.0 }, lastDay = null, recordedHours = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            ReadingHistogram(bins = List(24) { Double.NaN }, lastDay = null, recordedHours = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            ReadingHistogram(bins = List(24) { 0.0 }, lastDay = 1, recordedHours = setOf(24))
        }
    }
}
