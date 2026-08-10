package fr.vbrosseau.freshrssdiscover.presentation.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Permission required by the reading reminder from Android 13 on.
 *
 * Declared in the manifest regardless of version: below Android 13 the system
 * ignores it and notifications are granted by default.
 */
private const val NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

/**
 * Decides whether to ask for the notification permission (SPECS.md §4.9).
 *
 * Pure function, separated from the gesture it decides: it is the only part
 * of this request containing a rule, and the only one verifiable without a
 * device.
 *
 * - [isFirstCreation]: a screen rotation recreates the activity without any
 *   user action. Asking again then would show the system dialog on every
 *   configuration change, exactly the insistence §4.9 rejects.
 * - [sdkInt]: below Android 13 there is no notification permission. Asking
 *   would not fail loudly; it would be silently denied, which would look
 *   like a user refusal.
 * - [isGranted]: re-asking for a granted permission shows nothing.
 *
 * No fourth condition on a past refusal: the system stops showing the dialog
 * on its own after a firm denial, and layering an insistent explanation on
 * top is precisely what §4.9 rules out. A refusal takes nothing else from the
 * app; only the reminder stays silent.
 */
fun shouldAskForNotificationPermission(
    sdkInt: Int,
    isGranted: Boolean,
    isFirstCreation: Boolean,
): Boolean = isFirstCreation && sdkInt >= Build.VERSION_CODES.TIRAMISU && !isGranted

/**
 * An activity's permission request, registered then launched when needed.
 *
 * A class rather than a function: the result contract must be registered
 * before the activity reaches the started state, while the request itself
 * departs from `onCreate`. The two moments cannot fit in a single call.
 */
class NotificationPermissionRequest(private val activity: ComponentActivity) {

    /**
     * The result is deliberately ignored.
     *
     * Nothing depends on it: no screen, no loading, no setting. A refusal
     * leaves the whole app working, the §4.9 reminder simply stays silent,
     * and the settings switch can still turn it off for good.
     */
    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * @param isFirstCreation true when the activity is not being recreated,
     *   i.e. when `savedInstanceState` is null.
     */
    fun askIfNeeded(isFirstCreation: Boolean) {
        val granted = ContextCompat.checkSelfPermission(activity, NOTIFICATION_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        if (!shouldAskForNotificationPermission(Build.VERSION.SDK_INT, granted, isFirstCreation)) return
        launcher.launch(NOTIFICATION_PERMISSION)
    }
}
