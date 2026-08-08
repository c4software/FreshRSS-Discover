package fr.vbrosseau.freshrssdiscover.presentation.permission

import android.os.Build
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La règle de la demande de permission, éprouvée sans appareil.
 *
 * C'est tout ce que la demande contient de décision : le reste est
 * l'enregistrement d'un contrat de résultat, que seul le système sait exécuter.
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
     * Sous Android 13 il n'existe aucune permission de notification : la
     * demander serait refusée en silence, et laisserait croire à un refus de
     * l'utilisateur.
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
     * Une rotation d'écran recrée l'activité sans que l'utilisateur ait rien
     * demandé : redemander alors ferait réapparaître la boîte de dialogue à
     * chaque changement de configuration.
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

    /** Un refus antérieur ne change rien ici : c'est le système qui cesse d'afficher la demande. */
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
