package fr.vbrosseau.freshrssdiscover.reminder

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram

/**
 * Records when reading happens, and serves the accumulated histogram.
 *
 * An interface for the same reason as [OpeningRecorder]: the repository that
 * marks articles read and the scheduler that aims the reminder can both be
 * tested with a fake, without `DataStore`, clock or time zone to pass in —
 * the store owns all three.
 */
interface ReadingSessionRecorder {

    /**
     * Records that the user is reading right now.
     *
     * Callable on every batch of marked articles: the store keeps at most one
     * session per day and per hour ([ReadingHistogram.record]), so the cost of
     * a redundant call is one read, not a skewed histogram.
     */
    suspend fun recordSession()

    /** The histogram as accumulated so far, [ReadingHistogram.Empty] included. */
    suspend fun histogram(): ReadingHistogram
}
