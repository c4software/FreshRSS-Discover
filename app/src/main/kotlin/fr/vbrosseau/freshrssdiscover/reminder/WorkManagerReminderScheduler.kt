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
 * Nom du travail unique portant le rappel.
 *
 * Constant, et c'est tout l'intérêt : c'est lui qui fait qu'une centaine
 * d'ouvertures de l'application ne laissent qu'un seul rappel en attente, et
 * que [WorkManagerReminderScheduler.cancel] sait quoi annuler sans tenir de
 * registre d'identifiants.
 */
internal const val REMINDER_WORK_NAME = "rappel-de-lecture"

/**
 * Programme le rappel avec WorkManager (SPECS.md §4.9).
 *
 * **Un travail ponctuel réarmé, et non un travail périodique.** L'heure du
 * rappel n'est pas un intervalle : elle est celle de l'ouverture de la veille,
 * et elle change donc d'un jour à l'autre. `PeriodicWorkRequest` ne sait
 * exprimer qu'une période, et sa fenêtre d'exécution dérive — le rappel finirait
 * par tomber à une heure que personne n'a choisie.
 *
 * **Volontairement sans portée `@Singleton`.** L'objet ne retient rien, et sa
 * `ZoneId` est lue à sa construction : un singleton figerait pour toute la vie
 * du processus le fuseau du démarrage, et l'utilisateur qui change de pays
 * verrait son rappel rester à l'heure de l'ancien.
 */
internal class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val openingRecorder: OpeningRecorder,
    private val clock: Clock,
    private val zone: ZoneId,
) : ReminderScheduler {

    /**
     * Sans heure enregistrée, **rien n'est programmé**.
     *
     * Le cas se produit à la toute première ouverture, avant que
     * l'[OpeningRecorder] n'ait écrit quoi que ce soit. Choisir une heure par
     * défaut serait exactement ce que §4.9 refuse — un rappel à une heure de
     * développeur — et la prochaine ouverture programmera de toute façon.
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
