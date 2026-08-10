package fr.vbrosseau.freshrssdiscover.reminder

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.vbrosseau.freshrssdiscover.domain.reminder.nextReminderAt
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Name of the unique work carrying the reminder.
 *
 * Constant by design: it ensures a hundred app openings leave a single
 * pending reminder, and lets [WorkManagerReminderScheduler.cancel] know what
 * to cancel without an id registry.
 */
internal const val REMINDER_WORK_NAME = "rappel-de-lecture"

/**
 * Schedules the reminder with WorkManager (SPECS.md §4.9).
 *
 * A re-armed one-time work, not a periodic one. The reminder time is not an
 * interval: it is the previous day's opening time, and changes from day to
 * day. `PeriodicWorkRequest` can only express a period, and its execution
 * window drifts; the reminder would end up at a time nobody chose.
 *
 * Deliberately not `@Singleton`. The object holds nothing, and its `ZoneId`
 * is read at construction: a singleton would freeze the startup time zone
 * for the life of the process, keeping the reminder on the old time for a
 * user who changes country.
 */
internal class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val openingRecorder: OpeningRecorder,
    private val clock: Clock,
    private val zone: ZoneId,
) : ReminderScheduler {

    /**
     * Without a recorded time, nothing is scheduled.
     *
     * This happens on the very first opening, before the [OpeningRecorder]
     * has written anything. Picking a default time would be exactly what
     * §4.9 refuses (a developer-chosen hour), and the next opening will
     * schedule anyway.
     */
    override suspend fun scheduleNext() {
        val minute = openingRecorder.openingMinute() ?: return

        val now = clock.nowEpochMillis()
        workManager.enqueueUniqueWork(
            REMINDER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReadingReminderWorker>()
                .setInitialDelay(nextReminderAt(minute, now, zone) - now, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(REMINDER_WORK_NAME)
    }
}
