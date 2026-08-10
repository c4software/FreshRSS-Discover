package fr.vbrosseau.freshrssdiscover.presentation.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forces transmission of read markings when the app goes to the background.
 *
 * The batching in `ReadTransmissionScheduler` holds markings for five seconds
 * before sending (SPECS.md §4.5). Nothing is lost if the user leaves during
 * that window (the queue persists and replay at next startup drains it), but
 * in the meantime the server and other devices are unaware of a read that did
 * happen. Leaving the app within five seconds of a read is common; this
 * observer closes the window instead of waiting it out.
 *
 * `ON_STOP`, not `ON_PAUSE`: `ON_PAUSE` fires whenever another window comes
 * in front, including system dialogs, an expanded notification, or a
 * permission picker, while the app stays visible and reading resumes within
 * a second. Flushing on each of those events would defeat the batching in
 * exactly the case it covers. `ON_STOP` only occurs when the app is no longer
 * visible: nothing more will be read, so waiting is pointless.
 *
 * The app can be killed without this event arriving; the system guarantees no
 * callback when it reclaims a background process. That is not a defect to fix
 * here: it is exactly why the queue is persistent and only acknowledged after
 * server confirmation. This observer shortens the usual delay; it does not
 * replace replay at startup.
 *
 * Application scope, never a ViewModel's: the flush is triggered by the
 * screen going away, so launched in `viewModelScope` it would be cancelled by
 * the very event that requested it. Hence [ApplicationScope], whose
 * `SupervisorJob` lives as long as the process.
 *
 * A `DefaultLifecycleObserver` rather than a `@Composable` observing
 * `LocalLifecycleOwner`: the app, not a screen, has something to flush. Tying
 * it to a Composable would bind synchronization to the presence of a given
 * screen in the tree and require re-adding it on every new screen.
 *
 * Register once, in `MainActivity.onCreate()`, on the single `Activity`'s
 * lifecycle:
 *
 * ```kotlin
 * lifecycle.addObserver(readFlushOnBackgroundObserver)
 * ```
 *
 * The process lifecycle (`ProcessLifecycleOwner`) would be more accurate: it
 * produces one `ON_STOP` per actual backgrounding, whereas an `Activity`'s
 * also fires on every rotation and every article opened in the custom tab.
 * It requires the `androidx.lifecycle:lifecycle-process` dependency, absent
 * from the repository. The cost of doing without is nil: flushing an empty
 * queue touches no network, and remote marking is idempotent. This class
 * observes nothing itself, so if the dependency is added later only the
 * registration line changes.
 */
@Singleton
class ReadFlushOnBackgroundObserver @Inject constructor(
    private val readSyncRepository: ReadSyncRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : DefaultLifecycleObserver {

    /**
     * The outcome is deliberately ignored: a failed flush leaves the queue
     * intact, and there is no screen left to report it to.
     */
    override fun onStop(owner: LifecycleOwner) {
        applicationScope.launch { readSyncRepository.flush() }
    }
}
