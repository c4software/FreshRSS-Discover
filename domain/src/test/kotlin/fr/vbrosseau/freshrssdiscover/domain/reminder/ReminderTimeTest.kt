package fr.vbrosseau.freshrssdiscover.domain.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val SufficientEveningHistogram: ReadingHistogram =
    ReadingHistogram.Empty
        .record(day = 1, hour = 21)
        .record(day = 1, hour = 20)
        .record(day = 1, hour = 22)
        .record(day = 2, hour = 21)

private val OpeningAtNoon = DailyMinute(12 * MINUTES_PER_HOUR)

/**
 * The resolution order is the rule under test: fixed beats learned, learned
 * beats opening, and opening is the floor — not a developer default.
 */
class ReminderTimeTest {
    @Test
    fun aFixedHourWinsOverEverything() {
        val fixed = ReminderTime.Fixed(DailyMinute(7 * MINUTES_PER_HOUR + 30))

        val target = reminderTargetMinute(fixed, SufficientEveningHistogram, OpeningAtNoon)

        assertEquals(DailyMinute(7 * MINUTES_PER_HOUR + 30), target)
    }

    @Test
    fun aSufficientHistogramAimsAtTheStartOfItsDominantHour() {
        val target = reminderTargetMinute(ReminderTime.Automatic, SufficientEveningHistogram, OpeningAtNoon)

        assertEquals(DailyMinute(21 * MINUTES_PER_HOUR), target)
    }

    @Test
    fun anInsufficientHistogramFallsBackOnTheOpeningMinute() {
        val target = reminderTargetMinute(ReminderTime.Automatic, ReadingHistogram.Empty, OpeningAtNoon)

        assertEquals(OpeningAtNoon, target)
    }

    @Test
    fun nothingKnownMeansNoReminder() {
        assertNull(reminderTargetMinute(ReminderTime.Automatic, ReadingHistogram.Empty, openingMinute = null))
    }

    @Test
    fun aFixedHourNeedsNeitherHistoryNorOpening() {
        val fixed = ReminderTime.Fixed(DailyMinute(0))

        val target = reminderTargetMinute(fixed, ReadingHistogram.Empty, openingMinute = null)

        assertEquals(DailyMinute(0), target)
    }
}
