package fr.vbrosseau.freshrssdiscover.presentation.discover

import org.junit.Test
import kotlin.test.assertEquals

private const val NOW_SECONDS = 1_700_000_000L
private const val NOW_MILLIS = NOW_SECONDS * 1_000L

private const val MINUTE = 60L
private const val HOUR = 60L * MINUTE
private const val DAY = 24L * HOUR

class RelativeTimeTest {

    private fun ago(seconds: Long): RelativeTime =
        relativeTimeSince(publishedAtEpochSeconds = NOW_SECONDS - seconds, nowEpochMillis = NOW_MILLIS)

    @Test
    fun anArticleOfTheLastMinuteHasNoNumber() {
        // « il y a 0 min » serait absurde.
        assertEquals(RelativeTime.JustNow, ago(0))
        assertEquals(RelativeTime.JustNow, ago(MINUTE - 1))
    }

    @Test
    fun minutesStartAtTheFirstFullMinute() {
        assertEquals(RelativeTime.Minutes(1), ago(MINUTE))
        assertEquals(RelativeTime.Minutes(59), ago(HOUR - 1))
    }

    @Test
    fun hoursStartAtTheFirstFullHour() {
        assertEquals(RelativeTime.Hours(1), ago(HOUR))
        assertEquals(RelativeTime.Hours(2), ago(2 * HOUR))
        assertEquals(RelativeTime.Hours(23), ago(DAY - 1))
    }

    @Test
    fun daysStartAtTheFirstFullDay() {
        assertEquals(RelativeTime.Days(1), ago(DAY))
        assertEquals(RelativeTime.Days(29), ago(30 * DAY - 1))
    }

    @Test
    fun monthsTakeOverAfterThirtyDays() {
        assertEquals(RelativeTime.Months(1), ago(30 * DAY))
        assertEquals(RelativeTime.Months(12), ago(365 * DAY - 1))
    }

    @Test
    fun yearsTakeOverAfterThreeHundredAndSixtyFiveDays() {
        assertEquals(RelativeTime.Years(1), ago(365 * DAY))
        assertEquals(RelativeTime.Years(3), ago(3 * 365 * DAY))
    }

    @Test
    fun aFutureDateIsShownAsJustNow() {
        // Horloge de serveur en avance, article postdaté : « il y a -3 min »
        // serait faux *et* illisible.
        assertEquals(RelativeTime.JustNow, ago(-HOUR))
    }
}
