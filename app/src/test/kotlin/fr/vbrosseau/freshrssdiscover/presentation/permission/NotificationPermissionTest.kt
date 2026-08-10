package fr.vbrosseau.freshrssdiscover.presentation.permission

import android.os.Build
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The permission-request rule, tested without a device.
 *
 * This is all the decision logic the request contains; the rest is registering
 * a result contract that only the system can execute.
 */
class NotificationPermissionTest {

    private val below = Build.VERSION_CODES.TIRAMISU - 1

    @Test
    fun theFirstLaunchOfAModernAndroidAsksForThePermission() {
        assertTrue(
            shouldAskForNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isGranted = false,
                isFirstCreation = true,
            ),
        )
    }

    /**
     * Below Android 13 there is no notification permission: requesting it
     * would be silently denied and look like a user refusal.
     */
    @Test
    fun anOlderAndroidHasNothingToBeAskedFor() {
        assertFalse(
            shouldAskForNotificationPermission(sdkInt = below, isGranted = false, isFirstCreation = true),
        )
    }

    @Test
    fun anAlreadyGrantedPermissionIsNotAskedAgain() {
        assertFalse(
            shouldAskForNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isGranted = true,
                isFirstCreation = true,
            ),
        )
    }

    /**
     * A screen rotation recreates the activity without any user action: asking
     * again would bring the dialog back on every configuration change.
     */
    @Test
    fun aRecreatedActivityDoesNotAskAgain() {
        assertFalse(
            shouldAskForNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isGranted = false,
                isFirstCreation = false,
            ),
        )
    }

    /** A prior refusal changes nothing here: the system is what stops showing the dialog. */
    @Test
    fun aLaterAndroidVersionStillAsks() {
        assertTrue(
            shouldAskForNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU + 2,
                isGranted = false,
                isFirstCreation = true,
            ),
        )
    }
}
