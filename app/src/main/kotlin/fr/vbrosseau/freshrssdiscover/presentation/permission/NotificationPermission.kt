package fr.vbrosseau.freshrssdiscover.presentation.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * La permission qu'exige le rappel de lecture, à partir d'Android 13.
 *
 * Elle est déclarée au manifeste quelle que soit la version : en dessous
 * d'Android 13, le système l'ignore et les notifications sont accordées
 * d'office.
 */
private const val NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

/**
 * Y a-t-il lieu de demander la permission de notifier (SPECS.md §4.9) ?
 *
 * Fonction pure, séparée du geste qu'elle décide : c'est la seule part de cette
 * demande qui contienne une règle, et la seule qui se vérifie sans appareil.
 *
 * Les trois conditions se lisent dans l'ordre où elles éliminent :
 *
 * - **[isFirstCreation]** — une rotation d'écran recrée l'activité sans que
 *   l'utilisateur ait rien demandé. Redemander à cette occasion ferait
 *   réapparaître la boîte de dialogue système à chaque changement de
 *   configuration, ce qui est exactement l'insistance que §4.9 refuse.
 * - **[sdkInt]** — sous Android 13 il n'existe aucune permission de
 *   notification. La demander n'échouerait pas bruyamment ; elle serait
 *   simplement refusée en silence, ce qui laisserait croire à un refus de
 *   l'utilisateur.
 * - **[isGranted]** — redemander une permission déjà accordée n'affiche rien,
 *   mais dit du même coup que l'appelant ne sait pas ce qu'il possède.
 *
 * Aucune quatrième condition sur un refus passé : le système cesse de lui-même
 * d'afficher la demande une fois l'utilisateur ferme, et lui superposer une
 * explication insistante serait précisément ce que §4.9 écarte. Un refus ne
 * retire rien d'autre à l'application — seul le rappel reste muet.
 */
fun shouldAskForNotificationPermission(
    sdkInt: Int,
    isGranted: Boolean,
    isFirstCreation: Boolean,
): Boolean = isFirstCreation && sdkInt >= Build.VERSION_CODES.TIRAMISU && !isGranted

/**
 * La demande de permission d'une activité, enregistrée puis lancée au besoin.
 *
 * Une classe et non une fonction : le contrat de résultat doit être enregistré
 * **avant** que l'activité n'atteigne l'état démarré, alors que la demande, elle,
 * part depuis `onCreate`. Les deux moments ne peuvent donc pas tenir dans un
 * seul appel.
 */
class NotificationPermissionRequest(private val activity: ComponentActivity) {

    /**
     * Le résultat est volontairement ignoré.
     *
     * Rien n'en dépend : ni écran, ni chargement, ni réglage. Un refus laisse
     * l'application entière fonctionner, le rappel de §4.9 restant simplement
     * sans voix — et l'interrupteur des réglages permet toujours de l'éteindre
     * pour de bon.
     */
    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * @param isFirstCreation vrai lorsque l'activité n'est pas recréée, c'est-à-dire
     *   lorsque `savedInstanceState` est nul.
     */
    fun askIfNeeded(isFirstCreation: Boolean) {
        val granted = ContextCompat.checkSelfPermission(activity, NOTIFICATION_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        if (!shouldAskForNotificationPermission(Build.VERSION.SDK_INT, granted, isFirstCreation)) return
        launcher.launch(NOTIFICATION_PERMISSION)
    }
}
