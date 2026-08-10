package fr.vbrosseau.freshrssdiscover.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.vbrosseau.freshrssdiscover.data.local.localDayOf
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.CACHED_FEED_LIMIT
import fr.vbrosseau.freshrssdiscover.domain.reminder.reminderPlanFor
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject

/**
 * Decides on the day's reminder and re-arms it (SPECS.md §4.9).
 *
 * Separated from the worker that hosts it: a `CoroutineWorker` already takes
 * two assisted parameters, and everything the decision needs would have to
 * be added there. The decision lives in a plain class a test can construct
 * directly; the worker only calls it.
 *
 * No network access, ever. SPECS.md §2 excludes background synchronization:
 * only the cache is read, via `unreadFromCache`.
 */
internal class ReadingReminder @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val articleRepository: ArticleRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    /**
     * Notifies if warranted, then re-arms the next day's reminder.
     *
     * The order of the three refusals is not interchangeable:
     *
     * 1. No session: no notification and no re-arm. A logged-out user has no
     *    articles to read; keeping the chain running would wake the worker
     *    daily for nothing. The next login restarts it from app opening.
     * 2. Setting off: same treatment, same reason: re-arming a reminder the
     *    user disabled amounts to not having disabled it.
     * 3. Nothing to read: no notification, but re-arm. An empty cache today
     *    says nothing about tomorrow, and stopping here would permanently
     *    kill the reminder on the first day the user has read everything.
     */
    suspend fun remind() {
        if (authRepository.observeSession().first() == null) return
        if (!settingsRepository.observeReminderEnabled().first()) return

        val unread = articleRepository.unreadFromCache(CACHED_FEED_LIMIT)
        reminderPlanFor(unread, localDayOf(clock.nowEpochMillis(), zone))?.let(notifier::show)

        scheduler.scheduleNext()
    }
}

/**
 * Worker WorkManager wakes at reminder time.
 *
 * `CoroutineWorker` rather than `Worker`: the decision reads the cache and
 * the `DataStore`, both suspending.
 *
 * Always returns `Result.success()`. There is nothing to retry: the cache is
 * local, and a reminder delayed fifteen minutes by a retry would arrive
 * after the moment it was for.
 */
@HiltWorker
internal class ReadingReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val reminder: ReadingReminder,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        reminder.remind()

        return Result.success()
    }
}
