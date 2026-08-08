package fr.vbrosseau.freshrssdiscover.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.local.ReminderTimeStore
import fr.vbrosseau.freshrssdiscover.reminder.OpeningRecorder
import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler
import fr.vbrosseau.freshrssdiscover.reminder.WorkManagerReminderScheduler
import java.time.ZoneId

/**
 * Le rappel de lecture (SPECS.md §4.9) : ce qui le programme, et ce qui retient
 * l'heure qu'il vise.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReminderModule {

    @Binds
    abstract fun bindReminderScheduler(implementation: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindOpeningRecorder(implementation: ReminderTimeStore): OpeningRecorder

    companion object {

        /**
         * **Seul endroit du projet appelant `ZoneId.systemDefault()`**, pour la
         * raison exacte qui fait que `Clock` existe (voir [TimeModule]) : le
         * fuseau est un réglage du système, et le lire là où on en a besoin rend
         * intestable tout ce qui en dépend. Le rappel étant calculé en heure
         * locale, un test doit pouvoir se placer à Tokyo comme à Paris — et
         * surtout franchir un changement d'heure sans attendre mars.
         *
         * **Sans `@Singleton`, délibérément.** Le fuseau du téléphone change en
         * cours d'exécution : voyage, retour d'un vol, correction manuelle. Une
         * valeur mise en cache figerait celui du démarrage du processus, et le
         * rappel resterait à l'heure du pays quitté jusqu'au prochain
         * redémarrage. Les deux objets qui la consomment — `ReminderTimeStore`
         * et `WorkManagerReminderScheduler` — sont eux-mêmes construits à la
         * demande pour que cette fraîcheur leur parvienne.
         */
        @Provides
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()

        /**
         * L'instance configurée par l'application, et non une nouvelle : c'est
         * elle qui porte la fabrique de travailleurs de Hilt, sans laquelle
         * `ReadingReminderWorker` ne peut pas être construit.
         */
        @Provides
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
