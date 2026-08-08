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
 * Décide du rappel du jour et le réarme (SPECS.md §4.9).
 *
 * Séparé du travailleur qui le porte, et ce n'est pas une précaution de style :
 * un `CoroutineWorker` reçoit déjà deux paramètres assistés, et tout ce dont la
 * décision a besoin devrait s'y ajouter. La décision est ici, dans une classe
 * ordinaire qu'un test construit directement, et le travailleur n'a plus qu'à
 * l'appeler.
 *
 * **Aucun accès réseau, jamais.** SPECS.md §2 exclut toujours la synchronisation
 * en arrière-plan : seul le cache est lu, par `unreadFromCache`.
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
     * Notifie s'il y a lieu, puis réarme le rappel du lendemain.
     *
     * L'ordre des trois refus n'est pas interchangeable :
     *
     * 1. **Pas de session** — on ne notifie pas, et on **ne réarme pas**. Un
     *    utilisateur déconnecté n'a pas d'articles à lire ; laisser la chaîne
     *    tourner ferait revenir le travailleur tous les jours pour ne rien
     *    faire. La prochaine connexion la relancera depuis l'ouverture de
     *    l'application.
     * 2. **Réglage éteint** — même traitement, et pour la même raison : réarmer
     *    un rappel que l'utilisateur a coupé, c'est ne pas l'avoir coupé.
     * 3. **Rien à lire** — on ne notifie pas, mais on **réarme**. Un cache vide
     *    aujourd'hui ne dit rien de demain, et s'arrêter là éteindrait le rappel
     *    définitivement au premier jour où l'utilisateur a tout lu — c'est-à-dire
     *    au meilleur des jours.
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
 * Le travailleur que WorkManager réveille à l'heure du rappel.
 *
 * `CoroutineWorker` et non `Worker` : la décision lit le cache et le `DataStore`,
 * qui sont l'un et l'autre suspendus.
 *
 * Il rend toujours `Result.success()`. Il n'y a rien à réessayer — le cache est
 * local, et un rappel repoussé d'un quart d'heure par une reprise arriverait de
 * toute façon après le moment auquel il servait.
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
