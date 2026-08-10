package fr.vbrosseau.freshrssdiscover.presentation.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.reminder.OpeningRecorder
import fr.vbrosseau.freshrssdiscover.reminder.ReminderNotifier
import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminder-side effects of the app coming to the foreground (SPECS.md §4.9).
 *
 * The displayed reminder is dismissed: it has served its purpose once the
 * user arrives, and leaving it in the shade would make it stale clutter. Done
 * unconditionally: the caller need not know whether a reminder was showing or
 * already dismissed.
 *
 * The opening time is recorded and the next day's reminder scheduled. This is
 * the core of the §4.9 rule: the reminder fires at the hour the user
 * habitually opens the app, not at a developer-chosen time.
 *
 * `ON_START`, not `ON_RESUME`: `ON_RESUME` also fires on return from a system
 * dialog or from the custom tab showing an article, where the app never left.
 * Counting those returns as openings would drift the recorded time toward the
 * moment articles are closed. `ON_START` marks arrival on screen.
 *
 * Application scope: recording the time and scheduling the work survive
 * screen destruction, e.g. a rotation right after launch.
 *
 * Distinguishing the first opening of the day from later ones is not done
 * here: it belongs to the store, the only component that knows which day the
 * last recorded time was for. Deciding it here would force this observer to
 * hold state when it only relays an event.
 */
@Singleton
class ReminderOnForegroundObserver @Inject constructor(
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val openingRecorder: OpeningRecorder,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        notifier.dismiss()

        applicationScope.launch {
            openingRecorder.recordOpening()
            scheduler.scheduleNext()
        }
    }
}
