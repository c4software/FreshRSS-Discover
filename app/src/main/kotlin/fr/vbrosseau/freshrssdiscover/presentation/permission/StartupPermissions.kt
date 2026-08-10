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
 * Permission required to reach a server on the local network, from Android 17 on.
 *
 * SPECS.md §3.1 accepts a self-hosted instance on a local network on purpose.
 * Android 17's local network protection puts that behind a runtime permission;
 * below it, the permission does not exist and the system ignores it.
 */
private const val LOCAL_NETWORK_PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

/**
 * The permissions to ask for when the application starts, in order.
 *
 * Pure function, separated from the gesture it decides: it is the only part of
 * this request containing a rule, and the only one verifiable without a device
 * — all the more so as the tests run on an Android older than the local
 * network threshold.
 *
 * - [isFirstCreation]: a screen rotation recreates the activity without any
 *   user action. Asking again then would show the system dialogs on every
 *   configuration change, exactly the insistence §4.9 rejects.
 * - [sdkInt]: below Android 13 there is no notification permission, and below
 *   Android 17 no local network permission. Asking would not fail loudly; it
 *   would be silently denied, which would look like a user refusal.
 * - [isGranted]: re-asking for a granted permission shows nothing.
 *
 * No fourth condition on a past refusal: the system stops showing the dialog
 * on its own after a firm denial, and layering an insistent explanation on top
 * is precisely what §4.9 rules out. A refusal takes nothing else from the app:
 * the reminder stays silent, and a local instance falls back on the
 * "server unreachable" path the application already states (GOAL-030).
 */
fun permissionsToAskAtStartup(
    sdkInt: Int,
    isFirstCreation: Boolean,
    isGranted: (String) -> Boolean,
): List<String> {
    if (!isFirstCreation) return emptyList()
    return buildList {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU && !isGranted(NOTIFICATION_PERMISSION)) {
            add(NOTIFICATION_PERMISSION)
        }
        if (sdkInt >= Build.VERSION_CODES.CINNAMON_BUN && !isGranted(LOCAL_NETWORK_PERMISSION)) {
            add(LOCAL_NETWORK_PERMISSION)
        }
    }
}

/**
 * An activity's startup permission request, registered then launched when needed.
 *
 * A class rather than a function: the result contract must be registered
 * before the activity reaches the started state, while the request itself
 * departs from `onCreate`. The two moments cannot fit in a single call.
 *
 * One launcher for the whole list, not one per permission: the system shows a
 * single dialog at a time, and two requests fired from the same `onCreate`
 * would step on each other.
 */
class StartupPermissionsRequest(private val activity: ComponentActivity) {

    /**
     * The results are deliberately ignored.
     *
     * Nothing depends on them: no screen, no loading, no setting. A refusal
     * leaves the whole app working, the §4.9 reminder simply stays silent, the
     * settings switch can still turn it off for good, and a local server that
     * stops answering is reported like any unreachable server.
     */
    private val launcher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /**
     * @param isFirstCreation true when the activity is not being recreated,
     *   i.e. when `savedInstanceState` is null.
     */
    fun askIfNeeded(isFirstCreation: Boolean) {
        val permissions = permissionsToAskAtStartup(
            sdkInt = Build.VERSION.SDK_INT,
            isFirstCreation = isFirstCreation,
            isGranted = { permission ->
                ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            },
        )
        if (permissions.isEmpty()) return
        launcher.launch(permissions.toTypedArray())
    }
}
