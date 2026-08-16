package fr.vbrosseau.freshrssdiscover.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.local.ReadingHistogramStore
import fr.vbrosseau.freshrssdiscover.data.local.ReminderTimeStore
import fr.vbrosseau.freshrssdiscover.reminder.AndroidReminderNotifier
import fr.vbrosseau.freshrssdiscover.reminder.OpeningRecorder
import fr.vbrosseau.freshrssdiscover.reminder.ReadingSessionRecorder
import fr.vbrosseau.freshrssdiscover.reminder.ReminderNotifier
import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler
import fr.vbrosseau.freshrssdiscover.reminder.WorkManagerReminderScheduler
import java.time.ZoneId

/**
 * Bindings for the reading reminder feature (SPECS.md §4.9): scheduling,
 * storage of the target time, and notification display.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReminderModule {

    @Binds
    abstract fun bindReminderScheduler(implementation: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindOpeningRecorder(implementation: ReminderTimeStore): OpeningRecorder

    @Binds
    abstract fun bindReadingSessionRecorder(implementation: ReadingHistogramStore): ReadingSessionRecorder

    /**
     * The worker depends only on the interface, which makes it testable
     * without `NotificationManager`; this binding is the only place that
     * knows the Android implementation.
     */
    @Binds
    abstract fun bindReminderNotifier(implementation: AndroidReminderNotifier): ReminderNotifier

    companion object {

        /**
         * The only place in the project that calls `ZoneId.systemDefault()`,
         * for the same reason `Clock` exists (see [TimeModule]): the time zone
         * is a system setting, and reading it at the point of use makes
         * everything that depends on it untestable. The reminder is computed
         * in local time, so tests must be able to pin any zone and cross a DST
         * transition.
         *
         * Deliberately not `@Singleton`: the device time zone can change at
         * runtime (travel, manual correction). A cached value would freeze the
         * zone from process start, leaving the reminder at the old zone's time
         * until the next restart. The two consumers, `ReminderTimeStore` and
         * `WorkManagerReminderScheduler`, are themselves built on demand so
         * this freshness reaches them.
         */
        @Provides
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()

        /**
         * The instance configured by the application, not a new one: it
         * carries Hilt's worker factory, without which `ReadingReminderWorker`
         * cannot be constructed.
         */
        @Provides
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
