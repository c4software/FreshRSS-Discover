package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NOW = 1_700_000_000_000L
private const val ONE_MINUTE = 60_000L

class ForegroundReloadTest {
    @Test
    fun aColdStartReloads() {
        // Never backgrounded since creation: the app was killed, the feed
        // opens on what is new.
        assertTrue(reloadsOnForeground(lastBackgroundedAtEpochMillis = null, nowEpochMillis = NOW))
    }

    @Test
    fun aQuickSwitchToAnotherAppKeepsThePage() {
        assertFalse(reloadsOnForeground(NOW - 5 * ONE_MINUTE, NOW))
    }

    @Test
    fun comingBackAfterTheThresholdReloads() {
        assertTrue(reloadsOnForeground(NOW - FOREGROUND_RELOAD_THRESHOLD_MILLIS - ONE_MINUTE, NOW))
    }

    @Test
    fun theThresholdIsInclusive() {
        // "At least" reads literally, as for every threshold in the project.
        assertTrue(reloadsOnForeground(NOW - FOREGROUND_RELOAD_THRESHOLD_MILLIS, NOW))
        assertFalse(reloadsOnForeground(NOW - FOREGROUND_RELOAD_THRESHOLD_MILLIS + 1, NOW))
    }
}
