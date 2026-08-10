package fr.vbrosseau.freshrssdiscover

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.vbrosseau.freshrssdiscover.data.local.room.CacheMaintenance
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point and root of the injection graph.
 *
 * Carries no business logic: decisions live in `:domain`, network and disk
 * access in `data`. It only performs two startup steps: installing logging in
 * debug builds and launching the cache purge.
 */
@HiltAndroidApp
class FreshRssDiscoverApplication : Application(), Configuration.Provider {

    @Inject
    internal lateinit var cacheMaintenance: CacheMaintenance

    @Inject
    internal lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager is configured here rather than by its automatic
     * initializer, which is removed from the manifest.
     *
     * This is the only way to give it Hilt's worker factory, without which a
     * worker with injected dependencies cannot be constructed. The failure
     * would otherwise occur when the reminder runs, hours after startup, and
     * manifest as a reminder that never fires.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("Processus démarré")

        /*
         * Once per process start, and nothing waits on it.
         *
         * Purging after each page would mean twenty to thirty table scans per
         * session, each while the user is scrolling, where jank is visible.
         * A periodic purge would require WorkManager for an operation that has
         * no reason to run with the app closed. Here, the first render
         * (SPECS.md §5.1) is not blocked by anything.
         */
        cacheMaintenance.purgeExpiredInBackground()
    }
}
