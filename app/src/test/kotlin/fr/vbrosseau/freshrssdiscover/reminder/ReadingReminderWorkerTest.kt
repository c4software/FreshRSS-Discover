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
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderPlan
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

/** Minutes dans une heure, pour lire les attentes sans compter mentalement. */
private const val MINUTES_PER_HOUR = 60

/** L'instant que porte cette date locale à Paris. */
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

/** Ce qui a été affiché, sans `NotificationManager`. */
private class RecordingNotifier : ReminderNotifier {
    val shown: MutableList<ReminderPlan> = mutableListOf()

    override fun show(plan: ReminderPlan) {
        shown += plan
    }

    override fun dismiss() = Unit
}

/** Compte les réarmements : c'est eux que les cas de refus doivent supprimer. */
private class RecordingScheduler : ReminderScheduler {
    var scheduleCount: Int = 0
        private set

    override suspend fun scheduleNext() {
        scheduleCount++
    }

    override fun cancel() = Unit
}

/** Heure d'ouverture pilotée, sans `DataStore`. */
private class FakeOpeningRecorder(var minute: DailyMinute? = null) : OpeningRecorder {
    override suspend fun recordOpening() = Unit

    override suspend fun openingMinute(): DailyMinute? = minute
}

/**
 * Éprouve la décision du rappel **par le travailleur**, tel que WorkManager
 * l'exécutera : `TestListenableWorkerBuilder` construit un vrai
 * `CoroutineWorker` et lui donne les paramètres qu'il recevrait en production.
 *
 * Robolectric est indispensable ici — un `ListenableWorker` reçoit un `Context`
 * — mais rien d'autre n'est simulé : les dépendances de la décision sont les
 * Fakes du domaine.
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
     * Le travailleur est construit avec une fabrique explicite : sans elle,
     * WorkManager instancierait `ReadingReminderWorker` par son constructeur à
     * deux paramètres, que `@AssistedInject` ne laisse pas exister.
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
        // Exigence explicite : un utilisateur déconnecté n'a rien à lire, et
        // réarmer ferait revenir le travailleur chaque jour pour ne rien faire.
        articles.unreadInCache = listOf(article(id = 1L, title = "Un titre"))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(notifier.shown.isEmpty(), "Rien ne doit être affiché : ${notifier.shown}")
        assertEquals(0, scheduler.scheduleCount)
    }

    @Test
    fun aDisabledReminderNotifiesNothingAndSchedulesNothing() = runTest {
        // Réarmer un rappel que l'utilisateur a coupé, c'est ne pas l'avoir
        // coupé : il repartirait au prochain réveil du travailleur.
        signIn()
        settings.reminderEnabled.value = false
        articles.unreadInCache = listOf(article(id = 1L, title = "Un titre"))

        worker().doWork()

        assertTrue(notifier.shown.isEmpty(), "Rien ne doit être affiché : ${notifier.shown}")
        assertEquals(0, scheduler.scheduleCount)
    }

    @Test
    fun anEmptyCacheNotifiesNothingButStillSchedulesTheNextReminder() = runTest {
        // SPECS.md §4.9 : un rappel annonçant que la pile est vide est une
        // interruption sans contrepartie. Mais s'arrêter là éteindrait le rappel
        // définitivement le jour où l'utilisateur a tout lu — le meilleur des
        // jours.
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
        // SPECS.md §2 exclut toujours la synchronisation en arrière-plan : une
        // requête partie d'ici sortirait sans geste de l'utilisateur.
        signIn()
        articles.unreadInCache = listOf(article(id = 1L, title = "Le premier"))

        worker().doWork()

        assertEquals(0, articles.loadCallCount)
        assertEquals(0, articles.refreshCallCount)
    }

    @Test
    fun theSameDayGivesTheSameWordingTwice() = runTest {
        // SPECS.md §4.9 : deux exécutions du même jour — une reprise après
        // échec, un redémarrage — doivent donner le même message.
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
 * Éprouve la programmation sur un vrai WorkManager, celui de `work-testing` :
 * ce qui compte n'est pas qu'une méthode ait été appelée, mais qu'il reste
 * **un seul** travail en attente et au bon délai.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val recorder = FakeOpeningRecorder()
    private val clock = FakeClock(parisMillis(2026, 3, 4, hour = 10, minute = 0))

    @Before
    fun initializeWorkManager() {
        // `SynchronousExecutor` : sans lui, l'état du travail ne serait lisible
        // qu'après un aller-retour de fil d'exécution, et l'assertion
        // courserait la file.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun scheduler() =
        WorkManagerReminderScheduler(WorkManager.getInstance(context), recorder, clock, PARIS)

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

        // 8 h 12 est déjà passée aujourd'hui : la cible est demain, soit
        // 22 h 12 après l'instant courant.
        val expected = parisMillis(2026, 3, 5, hour = 8, minute = 12) - clock.nowEpochMillis()
        assertEquals(expected, reminderWork().single().initialDelayMillis)
        assertEquals(TimeUnit.HOURS.toMillis(22) + TimeUnit.MINUTES.toMillis(12), expected)
    }

    @Test
    fun schedulingTwiceLeavesASinglePendingReminder() = runTest {
        // Le programmateur est appelé à chaque ouverture de l'application :
        // sans travail unique, l'utilisateur recevrait autant de rappels qu'il
        // a lancé l'application la veille.
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
