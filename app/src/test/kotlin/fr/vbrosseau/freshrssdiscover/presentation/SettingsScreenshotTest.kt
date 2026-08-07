package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsAccount
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsScreen
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsUiState
import org.junit.Test

/**
 * Références visuelles de l'écran de réglages.
 *
 * Fichier distinct de `ScreensScreenshotTest` : l'écran a ses propres états —
 * dont une boîte de dialogue, qui n'existe nulle part ailleurs — et les
 * regrouper ici garde le rapport d'échec lisible.
 */
class SettingsScreenshotTest : ScreenshotTest() {

    @Test
    fun settingsScreen() {
        capture("reglages") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    visibleFractionPercent = VISIBLE_FRACTION_PERCENT,
                    continuousVisibilitySeconds = CONTINUOUS_VISIBILITY_SECONDS,
                    appVersion = APP_VERSION,
                ),
            )
        }
    }

    /**
     * La confirmation de déconnexion, par-dessus l'écran.
     *
     * C'est l'état le plus exposé aux défauts de contraste : le contenu passe
     * sous un voile assombrissant, et le bouton destructeur porte une couleur
     * qui lui est propre (SPECS.md §3.5, §7.1).
     */
    @Test
    fun settingsScreenAskingToConfirmSignOut() {
        capture("reglages-deconnexion") {
            settings(
                SettingsUiState(
                    account = ACCOUNT,
                    visibleFractionPercent = VISIBLE_FRACTION_PERCENT,
                    continuousVisibilitySeconds = CONTINUOUS_VISIBILITY_SECONDS,
                    appVersion = APP_VERSION,
                    isSignOutConfirmationVisible = true,
                ),
            )
        }
    }

    @Composable
    private fun settings(uiState: SettingsUiState) {
        SettingsScreen(
            uiState = uiState,
            onSignOutRequest = {},
            onSignOutConfirm = {},
            onSignOutDismiss = {},
        )
    }

    private companion object {
        val ACCOUNT = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice")
        const val APP_VERSION = "0.1.0"
        const val VISIBLE_FRACTION_PERCENT = 60
        const val CONTINUOUS_VISIBILITY_SECONDS = 1
    }
}
