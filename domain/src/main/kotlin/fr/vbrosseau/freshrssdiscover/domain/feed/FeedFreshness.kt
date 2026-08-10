package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlinx.coroutines.flow.Flow

/**
 * Past this delay without a server response, the displayed feed is stale
 * (SPECS.md §4.6).
 *
 * Six hours, not one or two: nothing syncs in the background (SPECS.md §2), so
 * the screen shows the cache until the user asks for something else. A short
 * threshold would turn the notice into a daily reflex, and a notice the user
 * learns to ignore says nothing. Six hours cleanly separates a session resumed
 * within the hour, where the feed is still the one left behind, from reopening
 * the next morning.
 */
const val STALE_FEED_THRESHOLD_MILLIS: Long = 6 * 60 * 60 * 1_000L

/**
 * Known freshness of the displayed feed, and what the user has already been
 * told about it.
 *
 * The computation lives here in `:domain` for the same reason as
 * `reminderPlanFor`: the current instant is passed in, never read. A rule that
 * queries its own clock can only be tested by waiting.
 *
 * @property lastRefreshEpochMillis last valid server response. `null` when
 *   there has never been one.
 * @property acknowledgedRefreshEpochMillis value of [lastRefreshEpochMillis]
 *   for which the notice was acknowledged. Keying the acknowledgement on the
 *   timestamp rather than a flag makes it expire on its own: a successful
 *   refresh changes the value, the acknowledgement stops matching, and the
 *   notice can reappear six hours later.
 */
data class FeedFreshness(
    val lastRefreshEpochMillis: Long? = null,
    val acknowledgedRefreshEpochMillis: Long? = null,
) {
    /**
     * Whether the displayed feed is older than [STALE_FEED_THRESHOLD_MILLIS].
     *
     * Without a reference point, nothing is stale. On the very first launch a
     * request is in flight: announcing a stale feed before the first response
     * would blame the server for a delay that does not exist.
     *
     * A clock moving backwards does not make anything stale either. The delta
     * becomes negative, the comparison is false, and that is the right
     * result: a "timestamp in the future = stale" rule would raise the notice
     * right after a successful refresh, on any clock adjustment or backup
     * restore. The timestamp corrects itself on the next server contact.
     */
    fun isStale(nowEpochMillis: Long): Boolean {
        val last = lastRefreshEpochMillis ?: return false
        return nowEpochMillis - last >= STALE_FEED_THRESHOLD_MILLIS
    }

    /**
     * Whether a notice should be shown: the feed is stale and the user has not
     * already dismissed it for this timestamp.
     */
    fun showsStaleNotice(nowEpochMillis: Long): Boolean =
        isStale(nowEpochMillis) && acknowledgedRefreshEpochMillis != lastRefreshEpochMillis
}

/**
 * Stores when the server last responded, and what the user acknowledged.
 *
 * Declared here, implemented in `:app`: the domain states what it needs
 * without knowing anything about storage (ARCHITECTURE.md §2).
 */
interface FeedFreshnessRepository {
    /** Emits on every change, timestamp writes and acknowledgements alike. */
    fun observeFreshness(): Flow<FeedFreshness>

    /**
     * Records that the server just responded.
     *
     * No parameter: the implementation reads the device clock. That is the
     * boundary; the [FeedFreshness] rule stays clockless, the write
     * necessarily has one.
     */
    suspend fun recordRefresh()

    /** The user dismissed the notice for the current timestamp. */
    suspend fun acknowledgeStale()
}
