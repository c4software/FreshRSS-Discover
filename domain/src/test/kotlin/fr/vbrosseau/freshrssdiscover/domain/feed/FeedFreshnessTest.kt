package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La règle d'ancienneté du flux (SPECS.md §4.6).
 *
 * Les cas qui comptent ne sont pas les six heures elles-mêmes — une soustraction
 * ne surprend personne — mais les trois situations où le calcul naïf se
 * tromperait : aucun rafraîchissement enregistré, une horloge d'appareil qui
 * recule, et un avis déjà acquitté que le temps doit pouvoir rouvrir.
 */
class FeedFreshnessTest {
    private val noon = 1_700_000_000_000L

    private fun hours(count: Long) = count * 60 * 60 * 1_000L

    @Test
    fun theThresholdIsSixHours() {
        assertEquals(hours(6), STALE_FEED_THRESHOLD_MILLIS)
    }

    @Test
    fun aFeedNeverRefreshedIsNotStale() {
        val freshness = FeedFreshness()

        assertFalse(freshness.isStale(noon))
        assertFalse(freshness.showsStaleNotice(noon))
    }

    @Test
    fun aFeedNeverRefreshedStaysSilentHoweverLateItGets() {
        val freshness = FeedFreshness()

        assertFalse(freshness.showsStaleNotice(noon + hours(240)))
    }

    @Test
    fun justUnderSixHoursIsNotStale() {
        val freshness = FeedFreshness(lastRefreshEpochMillis = noon)

        assertFalse(freshness.isStale(noon + hours(6) - 1))
    }

    @Test
    fun exactlySixHoursIsStale() {
        val freshness = FeedFreshness(lastRefreshEpochMillis = noon)

        assertTrue(freshness.isStale(noon + hours(6)))
    }

    @Test
    fun beyondSixHoursIsStale() {
        val freshness = FeedFreshness(lastRefreshEpochMillis = noon)

        assertTrue(freshness.isStale(noon + hours(7)))
    }

    @Test
    fun aClockThatWentBackwardsDoesNotMakeTheFeedStale() {
        val freshness = FeedFreshness(lastRefreshEpochMillis = noon)

        assertFalse(freshness.isStale(noon - hours(1)))
    }

    @Test
    fun aTimestampRestoredFromTheFutureDoesNotMakeTheFeedStale() {
        val freshness = FeedFreshness(lastRefreshEpochMillis = noon + hours(240))

        assertFalse(freshness.isStale(noon))
    }

    @Test
    fun aDeviceClockBeforeTheEpochDoesNotMakeTheFeedStale() {
        val freshness = FeedFreshness(lastRefreshEpochMillis = noon)

        assertFalse(freshness.isStale(-hours(1)))
    }

    @Test
    fun anAcknowledgedNoticeStaysSilentHoweverOldTheFeedGets() {
        val freshness =
            FeedFreshness(
                lastRefreshEpochMillis = noon,
                acknowledgedRefreshEpochMillis = noon,
            )

        assertTrue(freshness.isStale(noon + hours(12)))
        assertFalse(freshness.showsStaleNotice(noon + hours(12)))
    }

    @Test
    fun aSuccessfulRefreshAfterAnAcknowledgementLeavesNothingToSay() {
        val freshness =
            FeedFreshness(
                lastRefreshEpochMillis = noon + hours(7),
                acknowledgedRefreshEpochMillis = noon,
            )

        assertFalse(freshness.showsStaleNotice(noon + hours(7)))
    }

    @Test
    fun anAcknowledgementDoesNotSurviveTheNextRefreshGrowingOld() {
        val freshness =
            FeedFreshness(
                lastRefreshEpochMillis = noon + hours(7),
                acknowledgedRefreshEpochMillis = noon,
            )

        assertTrue(freshness.showsStaleNotice(noon + hours(13)))
    }

    @Test
    fun anAcknowledgementMadeBeforeAnyRefreshDoesNotSilenceTheFirstOne() {
        val freshness =
            FeedFreshness(
                lastRefreshEpochMillis = noon,
                acknowledgedRefreshEpochMillis = null,
            )

        assertTrue(freshness.showsStaleNotice(noon + hours(6)))
    }

    @Test
    fun aFreshFeedHasNothingToSayEvenUnacknowledged() {
        val freshness = FeedFreshness(lastRefreshEpochMillis = noon)

        assertFalse(freshness.showsStaleNotice(noon + hours(1)))
    }

    @Test
    fun twoFreshnessesWithTheSameValuesAreEqual() {
        val one = FeedFreshness(noon, noon)
        val other = FeedFreshness(noon, noon)

        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
        assertEquals(one, one.copy())
        assertTrue(one.toString().contains("FeedFreshness"))
    }
}
