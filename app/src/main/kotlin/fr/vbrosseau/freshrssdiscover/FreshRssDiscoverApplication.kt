package fr.vbrosseau.freshrssdiscover

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.vbrosseau.freshrssdiscover.data.local.room.CacheMaintenance
import timber.log.Timber
import javax.inject.Inject

/**
 * Point d'entrée de l'application et racine du graphe d'injection.
 *
 * Elle ne porte aucune logique métier : tout ce qui décide vit dans `:domain`,
 * tout ce qui parle au réseau ou au disque vit dans `data`. Elle se borne à
 * deux gestes de démarrage — installer la journalisation en construction de
 * débogage, et lancer la purge du cache.
 */
@HiltAndroidApp
class FreshRssDiscoverApplication : Application() {

    @Inject
    internal lateinit var cacheMaintenance: CacheMaintenance

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("Processus démarré")

        /*
         * Une fois par démarrage de processus, et rien ne l'attend.
         *
         * Après chaque page, ce serait vingt à trente balayages de table par
         * session, chacun pendant que l'utilisateur fait défiler — précisément
         * l'instant où une saccade se voit. Périodiquement, il faudrait
         * WorkManager pour une opération qui n'a aucune raison de tourner
         * application fermée. Ici, le premier affichage (SPECS.md §5.1) n'est
         * suspendu à rien.
         */
        cacheMaintenance.purgeExpiredInBackground()
    }
}
