package fr.vbrosseau.freshrssdiscover.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.FakeAuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.reminder.DailyMinute
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderPlan
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderTime
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** Minutes in an hour, so expectations read without mental arithmetic. */
private const val MINUTES_PER_HOUR = 60

/** The instant carried by this local date in Paris. */
private fun parisMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
): Long =
    ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), PARIS)
        .toInstant()
        .toEpochMilli()

/** Records what was shown, without a `NotificationManager`. */
private class RecordingNotifier : ReminderNotifier {
    val shown: MutableList<ReminderPlan> = mutableListOf()

    override fun show(plan: ReminderPlan) {
        shown += plan
    }

    override fun dismiss() = Unit
}

/** Counts reschedules: the refusal cases must suppress them. */
private class RecordingScheduler : ReminderScheduler {
    var scheduleCount: Int = 0
        private set

    override suspend fun scheduleNext() {
        scheduleCount++
    }

    override fun cancel() = Unit
}

/** Controlled opening time, without `DataStore`. */
private class FakeOpeningRecorder(var minute: DailyMinute? = null) : OpeningRecorder {
    override suspend fun recordOpening() = Unit

    override suspend fun openingMinute(): DailyMinute? = minute
}

/**
 * Tests the reminder decision through the worker, as WorkManager will execute
 * it: `TestListenableWorkerBuilder` builds a real `CoroutineWorker` and gives
 * it the parameters it would receive in production.
 *
 * Robolectric is required here (a `ListenableWorker` receives a `Context`),
 * but nothing else is simulated: the decision's dependencies are the domain
 * Fakes.
 */
@RunWith(RobolectricTestRunner::class)
class ReadingReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val sessions = MutableStateFlow<AuthSession?>(null)
    private val auth = FakeAuthRepository(sessions)
    private val settings = FakeSettingsRepository()
    private val articles = FakeArticleRepository()
    private val notifier = RecordingNotifier()
    private val scheduler = RecordingScheduler()
    private val clock = FakeClock(parisMillis(2026, 3, 4, hour = 20, minute = 0))

    private fun signIn() {
        val address = assertIs<ServerAddressResult.Valid>(ServerAddress.parse("rss.exemple.org")).address
        sessions.value = auth.sessionOf(address)
    }

    /**
     * The worker is built with an explicit factory: without it, WorkManager
     * would instantiate `ReadingReminderWorker` through its two-parameter
     * constructor, which `@AssistedInject` does not let exist.
     */
    private fun worker(): ReadingReminderWorker {
        val reminder = ReadingReminder(auth, settings, articles, notifier, scheduler, clock, PARIS)

        return TestListenableWorkerBuilder<ReadingReminderWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = ReadingReminderWorker(appContext, workerParameters, reminder)
                },
            )
            .build()
    }

    @Test
    fun aWorkerWithoutSessionNotifiesNothingAndSchedulesNothing() = runTest {
        // Explicit requirement: a signed-out user has nothing to read, and
        // rescheduling would bring the worker back daily to do nothing.
        articles.unreadInCache = listOf(article(id = 1L, title = "Un titre"))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(notifier.shown.isEmpty(), "Rien ne doit être affiché : ${notifier.shown}")
        assertEquals(0, scheduler.scheduleCount)
    }

    @Test
    fun aDisabledReminderNotifiesNothingAndSchedulesNothing() = runTest {
        // Rescheduling a reminder the user turned off means it was not turned
        // off: it would fire again at the worker's next wake-up.
        signIn()
        settings.reminderEnabled.value = false
        articles.unreadInCache = listOf(article(id = 1L, title = "Un titre"))

        worker().doWork()

        assertTrue(notifier.shown.isEmpty(), "Rien ne doit être affiché : ${notifier.shown}")
        assertEquals(0, scheduler.scheduleCount)
    }

    @Test
    fun anEmptyCacheNotifiesNothingButStillSchedulesTheNextReminder() = runTest {
        // SPECS.md §4.9: a reminder announcing an empty pile is an
        // interruption with no payoff. But stopping there would permanently
        // disable the reminder the day the user has read everything.
        signIn()

        worker().doWork()

        assertTrue(notifier.shown.isEmpty(), "Rien ne doit être affiché : ${notifier.shown}")
        assertEquals(1, scheduler.scheduleCount)
    }

    @Test
    fun aFilledCacheNotifiesWithRealTitlesAndTheRemainingCount() = runTest {
        signIn()
        articles.unreadInCache = listOf(
            article(id = 1L, title = "Le premier"),
            article(id = 2L, title = "Le deuxième"),
            article(id = 3L, title = "Le troisième"),
        )

        worker().doWork()

        val plan = notifier.shown.single()
        assertEquals(listOf("Le premier", "Le deuxième"), plan.titles)
        assertEquals(3, plan.unreadCount)
        assertEquals(1, scheduler.scheduleCount)
    }

    @Test
    fun theWorkerReadsTheCacheAndNeverTheNetwork() = runTest {
        // SPECS.md §2 always excludes background sync: a request from here
        // would leave without any user gesture.
        signIn()
        articles.unreadInCache = listOf(article(id = 1L, title = "Le premier"))

        worker().doWork()

        assertEquals(0, articles.loadCallCount)
        assertEquals(0, articles.refreshCallCount)
    }

    @Test
    fun theSameDayGivesTheSameWordingTwice() = runTest {
        // SPECS.md §4.9: two runs on the same day (a retry after failure, a
        // restart) must give the same message.
        signIn()
        articles.unreadInCache = listOf(article(id = 1L, title = "Le premier"))

        worker().doWork()
        clock.setTo(parisMillis(2026, 3, 4, hour = 23, minute = 30))
        worker().doWork()

        assertEquals(notifier.shown[0].tone, notifier.shown[1].tone)
    }

    @Test
    fun anotherDayGivesAnotherWording() = runTest {
        signIn()
        articles.unreadInCache = listOf(article(id = 1L, title = "Le premier"))

        worker().doWork()
        clock.setTo(parisMillis(2026, 3, 5, hour = 20, minute = 0))
        worker().doWork()

        assertTrue(
            notifier.shown[0].tone != notifier.shown[1].tone,
            "Deux jours de suite ne doivent pas partager leur formulation : ${notifier.shown}",
        )
    }
}

/**
 * Tests the scheduling against a real WorkManager, the `work-testing` one:
 * what matters is not that a method was called, but that exactly one work
 * remains pending, with the right delay.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val recorder = FakeOpeningRecorder()
    private val sessionRecorder = FakeReadingSessionRecorder()
    private val settingsRepository = FakeSettingsRepository()
    private val clock = FakeClock(parisMillis(2026, 3, 4, hour = 10, minute = 0))

    @Before
    fun initializeWorkManager() {
        // `SynchronousExecutor`: without it, the work state would only be
        // readable after a thread round-trip, and the assertion would race
        // the queue.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun scheduler() =
        WorkManagerReminderScheduler(
            WorkManager.getInstance(context),
            recorder,
            sessionRecorder,
            settingsRepository,
            clock,
            PARIS,
        )

    private fun reminderWork(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(REMINDER_WORK_NAME).get()

    @Test
    fun withoutARecordedOpeningNothingIsScheduled() = runTest {
        scheduler().scheduleNext()

        assertTrue(reminderWork().isEmpty(), "Aucun travail ne doit attendre : ${reminderWork()}")
    }

    @Test
    fun aRecordedOpeningSchedulesTheReminderForTheNextOccurrenceOfThatTime() = runTest {
        recorder.minute = DailyMinute(8 * MINUTES_PER_HOUR + 12)

        scheduler().scheduleNext()

        // 8:12 has already passed today: the target is tomorrow, 22 h 12 min
        // after the current instant.
        val expected = parisMillis(2026, 3, 5, hour = 8, minute = 12) - clock.nowEpochMillis()
        assertEquals(expected, reminderWork().single().initialDelayMillis)
        assertEquals(TimeUnit.HOURS.toMillis(22) + TimeUnit.MINUTES.toMillis(12), expected)
    }

    @Test
    fun aSufficientHistogramBeatsTheOpeningMinute() = runTest {
        // The heart of GOAL-035: the reminder aims at when the user reads,
        // not at when they happened to open the app.
        recorder.minute = DailyMinute(8 * MINUTES_PER_HOUR + 12)
        sessionRecorder.histogram = ReadingHistogram.Empty
            .record(day = 1, hour = 21)
            .record(day = 1, hour = 20)
            .record(day = 1, hour = 22)
            .record(day = 2, hour = 21)

        scheduler().scheduleNext()

        // 21:00 is still to come today: the delay is 11 h from 10:00.
        assertEquals(TimeUnit.HOURS.toMillis(11), reminderWork().single().initialDelayMillis)
    }

    @Test
    fun aFixedHourBeatsTheHistogram() = runTest {
        sessionRecorder.histogram = ReadingHistogram.Empty
            .record(day = 1, hour = 21)
            .record(day = 1, hour = 20)
            .record(day = 1, hour = 22)
        settingsRepository.reminderTime.value = ReminderTime.Fixed(DailyMinute(18 * MINUTES_PER_HOUR))

        scheduler().scheduleNext()

        assertEquals(TimeUnit.HOURS.toMillis(8), reminderWork().single().initialDelayMillis)
    }

    @Test
    fun anInsufficientHistogramFallsBackOnTheOpeningMinute() = runTest {
        recorder.minute = DailyMinute(8 * MINUTES_PER_HOUR + 12)
        sessionRecorder.histogram = ReadingHistogram.Empty.record(day = 1, hour = 21)

        scheduler().scheduleNext()

        val expected = parisMillis(2026, 3, 5, hour = 8, minute = 12) - clock.nowEpochMillis()
        assertEquals(expected, reminderWork().single().initialDelayMillis)
    }

    @Test
    fun schedulingTwiceLeavesASinglePendingReminder() = runTest {
        // The scheduler is called on every app opening: without unique work,
        // the user would get as many reminders as app launches the day before.
        recorder.minute = DailyMinute(8 * MINUTES_PER_HOUR + 12)
        val scheduler = scheduler()

        scheduler.scheduleNext()
        scheduler.scheduleNext()

        assertEquals(1, reminderWork().count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun cancellingRemovesThePendingReminder() = runTest {
        recorder.minute = DailyMinute(8 * MINUTES_PER_HOUR + 12)
        val scheduler = scheduler()
        scheduler.scheduleNext()

        scheduler.cancel()

        assertEquals(0, reminderWork().count { it.state == WorkInfo.State.ENQUEUED })
    }
}
