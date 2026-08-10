package fr.vbrosseau.freshrssdiscover.reminder

import fr.vbrosseau.freshrssdiscover.domain.reminder.DailyMinute

/**
 * Records the time of an application opening.
 *
 * An interface rather than a direct store call: it lets
 * `ReadingReminderWorkerTest` exercise the worker with a fake, without
 * `DataStore`, clock, or time zone to pass in; the store already owns both.
 */
interface OpeningRecorder {

    /**
     * Records the current time, if this is the first opening of the day.
     *
     * Ignoring later openings is the rule, not an optimization: the time
     * sought is when the user reaches for the app, not a distracted visit at
     * the end of the day.
     */
    suspend fun recordOpening()

    /** The recorded time, or `null` if the app has never been opened. */
    suspend fun openingMinute(): DailyMinute?
}
