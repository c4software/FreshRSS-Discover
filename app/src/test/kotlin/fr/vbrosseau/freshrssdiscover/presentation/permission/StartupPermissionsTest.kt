package fr.vbrosseau.freshrssdiscover.presentation.permission

import android.Manifest
import android.os.Build
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The permission-request rule, tested without a device.
 *
 * This is all the decision logic the request contains; the rest is registering
 * a result contract that only the system can execute. Robolectric pins the
 * simulated Android to 36 — below the local network threshold — so the rule
 * could not be observed there at all.
 */
class StartupPermissionsTest {

    private val notifications = Manifest.permission.POST_NOTIFICATIONS
    private val localNetwork = Manifest.permission.ACCESS_LOCAL_NETWORK

    private val nothingGranted: (String) -> Boolean = { false }
    private val everythingGranted: (String) -> Boolean = { true }

    @Test
    fun theFirstLaunchOfAndroid13AsksForNotificationsOnly() {
        assertEquals(
            listOf(notifications),
            permissionsToAskAtStartup(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isFirstCreation = true,
                isGranted = nothingGranted,
            ),
        )
    }

    /** Android 17 puts the local network behind a permission (SPECS.md §3.1). */
    @Test
    fun theFirstLaunchOfAndroid17AlsoAsksForTheLocalNetwork() {
        assertEquals(
            listOf(notifications, localNetwork),
            permissionsToAskAtStartup(
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN,
                isFirstCreation = true,
                isGranted = nothingGranted,
            ),
        )
    }

    /**
     * The exact boundary: one level below, the local network permission does
     * not exist and asking for it would be silently denied.
     */
    @Test
    fun theAndroidJustBeforeDoesNotAskForTheLocalNetwork() {
        assertEquals(
            listOf(notifications),
            permissionsToAskAtStartup(
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN - 1,
                isFirstCreation = true,
                isGranted = nothingGranted,
            ),
        )
    }

    /** Below Android 13 neither permission exists yet. */
    @Test
    fun anOlderAndroidHasNothingToBeAskedFor() {
        assertEquals(
            emptyList(),
            permissionsToAskAtStartup(
                sdkInt = Build.VERSION_CODES.TIRAMISU - 1,
                isFirstCreation = true,
                isGranted = nothingGranted,
            ),
        )
    }

    @Test
    fun alreadyGrantedPermissionsAreNotAskedAgain() {
        assertEquals(
            emptyList(),
            permissionsToAskAtStartup(
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN,
                isFirstCreation = true,
                isGranted = everythingGranted,
            ),
        )
    }

    /** Only the missing one is asked for, not the pair. */
    @Test
    fun onlyTheMissingPermissionIsAskedFor() {
        assertEquals(
            listOf(localNetwork),
            permissionsToAskAtStartup(
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN,
                isFirstCreation = true,
                isGranted = { it == notifications },
            ),
        )
    }

    /**
     * A screen rotation recreates the activity without any user action: asking
     * again would bring the dialogs back on every configuration change.
     */
    @Test
    fun aRecreatedActivityDoesNotAskAgain() {
        assertEquals(
            emptyList(),
            permissionsToAskAtStartup(
                sdkInt = Build.VERSION_CODES.CINNAMON_BUN,
                isFirstCreation = false,
                isGranted = nothingGranted,
            ),
        )
    }
}
