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

    @Test
    fun aMidnightDominantHourShiftsToTheEveningEdge() {
        // The observed case: evening reading spilling past midnight made
        // hour 0 the densest bin, and the reminder fired while nobody was
        // awake for it.
        val nightOwl =
            ReadingHistogram.Empty
                .record(day = 1, hour = 0)
                .record(day = 1, hour = 20)
                .record(day = 2, hour = 0)
                .record(day = 2, hour = 19)

        val target = reminderTargetMinute(ReminderTime.Automatic, nightOwl, OpeningAtNoon)

        assertEquals(DailyMinute(21 * MINUTES_PER_HOUR + 59), target)
    }

    @Test
    fun anEarlyMorningDominantHourShiftsToSeven() {
        val earlyBird =
            ReadingHistogram.Empty
                .record(day = 1, hour = 5)
                .record(day = 1, hour = 6)
                .record(day = 2, hour = 5)
                .record(day = 3, hour = 5)

        val target = reminderTargetMinute(ReminderTime.Automatic, earlyBird, OpeningAtNoon)

        assertEquals(DailyMinute(QUIET_NIGHT_END_MINUTE), target)
    }

    @Test
    fun anOpeningMinuteInsideTheNightShiftsToo() {
        val lateOpening = DailyMinute(23 * MINUTES_PER_HOUR + 30)

        val target = reminderTargetMinute(ReminderTime.Automatic, ReadingHistogram.Empty, lateOpening)

        assertEquals(DailyMinute(21 * MINUTES_PER_HOUR + 59), target)
    }

    @Test
    fun aFixedHourInsideTheNightIsAppliedAsIs() {
        val fixed = ReminderTime.Fixed(DailyMinute(23 * MINUTES_PER_HOUR))

        val target = reminderTargetMinute(fixed, SufficientEveningHistogram, OpeningAtNoon)

        assertEquals(DailyMinute(23 * MINUTES_PER_HOUR), target)
    }

    @Test
    fun theEdgesOfTheAllowedDayStayWhereTheyAre() {
        assertEquals(
            DailyMinute(QUIET_NIGHT_END_MINUTE),
            DailyMinute(QUIET_NIGHT_END_MINUTE).awayFromQuietNight(),
        )
        assertEquals(
            DailyMinute(QUIET_NIGHT_START_MINUTE - 1),
            DailyMinute(QUIET_NIGHT_START_MINUTE - 1).awayFromQuietNight(),
        )
    }

    @Test
    fun tenPastTenShiftsBackNotForward() {
        // 22:10 sits 11 minutes from the evening edge and hours from the
        // morning: the nearest-edge rule must not send it across the night.
        val target = DailyMinute(QUIET_NIGHT_START_MINUTE + 10).awayFromQuietNight()

        assertEquals(DailyMinute(QUIET_NIGHT_START_MINUTE - 1), target)
    }

    @Test
    fun theSmallHoursSplitBetweenEveningAndMorning() {
        // 02:29 is the last minute closer to the evening edge, 02:30 the
        // first closer to the morning: the split is deterministic, no tie.
        assertEquals(
            DailyMinute(QUIET_NIGHT_START_MINUTE - 1),
            DailyMinute(2 * MINUTES_PER_HOUR + 29).awayFromQuietNight(),
        )
        assertEquals(
            DailyMinute(QUIET_NIGHT_END_MINUTE),
            DailyMinute(2 * MINUTES_PER_HOUR + 30).awayFromQuietNight(),
        )
    }
}
