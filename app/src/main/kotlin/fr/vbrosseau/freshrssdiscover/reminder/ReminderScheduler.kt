package fr.vbrosseau.freshrssdiscover.reminder

/**
 * Schedules the next reminder (SPECS.md §4.9).
 *
 * Called from two places by design: at app opening, which sets the next
 * day's time, and by the worker itself after notifying. Without the second,
 * the chain would stop on the first day the app is not opened, which is
 * precisely the day the reminder is for.
 */
interface ReminderScheduler {

    /**
     * Schedules the next reminder, replacing the pending one.
     *
     * Idempotent: two calls yield a single reminder, which allows calling it
     * on every opening without bookkeeping.
     */
    suspend fun scheduleNext()

    /** Cancels the pending reminder (setting disabled, or logout). */
    fun cancel()
}
