package fr.vbrosseau.freshrssdiscover

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Point d'entrée de l'application et racine du graphe d'injection.
 *
 * Elle ne porte aucune logique métier : tout ce qui décide vit dans `:domain`,
 * tout ce qui parle au réseau ou au disque vit dans `data`. Son seul travail
 * propre est d'installer la journalisation, et uniquement en construction de
 * débogage.
 */
@HiltAndroidApp
class FreshRssDiscoverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("Processus démarré")
    }
}
