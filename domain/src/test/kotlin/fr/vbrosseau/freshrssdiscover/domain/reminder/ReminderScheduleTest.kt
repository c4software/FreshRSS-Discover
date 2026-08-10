package fr.vbrosseau.freshrssdiscover.domain.reminder

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

private fun at(
    zone: ZoneId,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

/**
 * Reminder time, tested where it can go wrong.
 *
 * These cases are not variants of one computation: they are the four ways a
 * date behaves unlike a subtraction: an instant already past, daylight saving
 * changes, the reader's time zone, and a blatantly wrong device clock.
 */
class ReminderScheduleTest {
    @Test
    fun aTimeStillToComeTodayIsKeptForToday() {
        val now = at(PARIS, 2026, 3, 10, 8, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 10, 20, 0), next)
    }

    @Test
    fun aTimeAlreadyPassedGoesToTomorrow() {
        val now = at(PARIS, 2026, 3, 10, 21, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 11, 20, 0), next)
    }

    @Test
    fun theExactSameMinuteGoesToTomorrowRatherThanFiringNow() {
        // The defining case of this function. The recorded time is the app
        // opening time; at the moment of scheduling, it has just struck.
        // Choosing "today" would fire the notification while the user reads.
        val now = at(PARIS, 2026, 3, 10, 20, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 11, 20, 0), next)
    }

    @Test
    fun theResultIsAlwaysInTheFuture() {
        val now = at(PARIS, 2026, 3, 10, 12, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            assertTrue(nextReminderAt(DailyMinute(minute), now, PARIS) > now, "minute $minute")
        }
    }

    @Test
    fun theHourSurvivesTheSpringClockChange() {
        // Night of March 28-29, 2026 in Paris: 2 a.m. becomes 3 a.m. A
        // "now + 24 h" computation would shift the reminder by one hour and
        // it would drift twice a year. The reminder must stay at 8 a.m.
        val now = at(PARIS, 2026, 3, 28, 9, 0)

        val next = nextReminderAt(DailyMinute(8 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 29, 8, 0), next)
    }

    @Test
    fun theHourSurvivesTheAutumnClockChange() {
        // Night of October 24-25, 2026: 3 a.m. becomes 2 a.m. again, the day
        // lasts twenty-five hours.
        val now = at(PARIS, 2026, 10, 24, 9, 0)

        val next = nextReminderAt(DailyMinute(8 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 10, 25, 8, 0), next)
    }

    @Test
    fun theReaderTimeZoneDecidesAndNotTheServerOne() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = at(tokyo, 2026, 3, 10, 21, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, tokyo)

        assertEquals(at(tokyo, 2026, 3, 11, 20, 0), next)
    }

    @Test
    fun theMomentOfAnInstantIsReadInTheReaderZone() {
        // 11 p.m. in Paris is 7 a.m. the next day in Tokyo: the same instant
        // is not the same moment of the day, and the reader's zone decides.
        val instant = at(PARIS, 2026, 3, 10, 23, 0)

        assertEquals(DailyMinute(23 * 60), DailyMinute.of(instant, PARIS))
        assertEquals(DailyMinute(7 * 60), DailyMinute.of(instant, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun aMomentOutsideTheDayIsRefusedAtConstruction() {
        // An out-of-bounds value comes from code or a corrupted disk, never
        // from the user: silencing it would schedule a reminder at an hour
        // that does not exist.
        assertFailsWith<IllegalArgumentException> { DailyMinute(MINUTES_PER_DAY) }
        assertFailsWith<IllegalArgumentException> { DailyMinute(-1) }
    }
}
