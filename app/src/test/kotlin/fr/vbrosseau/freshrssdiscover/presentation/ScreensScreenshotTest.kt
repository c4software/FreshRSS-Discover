package fr.vbrosseau.freshrssdiscover.presentation

import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppNavigationBar
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppRoutes
import org.junit.Test

/**
 * Références visuelles de l'ossature.
 *
 * Les écrans réels viendront s'ajouter ici au fil des Goals. Capturer dès
 * maintenant la barre de navigation et l'écran d'attente sert d'abord à
 * éprouver la chaîne Roborazzi elle-même : sans une référence enregistrée,
 * `verifyRoborazziDebug` ne compare rien et passerait à tort.
 */
class ScreensScreenshotTest : ScreenshotTest() {

    @Test
    fun placeholderScreen() {
        capture("ecran-attente") { PlaceholderScreen() }
    }

    @Test
    fun navigationBar() {
        capture("barre-navigation") {
            AppNavigationBar(currentRoute = AppRoutes.DISCOVER, onSelect = {})
        }
    }
}
