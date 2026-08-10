package fr.vbrosseau.freshrssdiscover.presentation.settings

import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler

/**
 * In-memory reminder scheduler for tests.
 *
 * It counts schedule and cancel calls separately: a single count would not
 * distinguish scheduling from cancelling, which is precisely what the settings
 * toggle must get right.
 */
class FakeReminderScheduler : ReminderScheduler {
    var scheduleCount: Int = 0
        private set

    var cancelCount: Int = 0
        private set

    override suspend fun scheduleNext() {
        scheduleCount++
    }

    override fun cancel() {
        cancelCount++
    }
}
