package fr.vbrosseau.freshrssdiscover.reminder

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderPlan

/**
 * Displays the reading reminder (SPECS.md §4.9).
 *
 * An interface despite a single implementation: it is the boundary between
 * deciding a reminder, testable without Android, and showing it, which
 * requires a `NotificationManager`. Without it the worker could only be
 * tested through instrumentation.
 */
interface ReminderNotifier {

    /**
     * Shows the reminder described by [plan].
     *
     * Silent if the system refuses notifications: the user already said no,
     * and failing would make the worker retry a refusal that will not change.
     */
    fun show(plan: ReminderPlan)

    /**
     * Removes the displayed reminder.
     *
     * Called when the app opens: the reminder has served its purpose once the
     * user arrives, and leaving it in the shade would let a second one stack
     * beside it the next day.
     *
     * No-op when nothing is shown: the caller need not know whether a
     * reminder was posted or already dismissed by the user.
     */
    fun dismiss()
}
