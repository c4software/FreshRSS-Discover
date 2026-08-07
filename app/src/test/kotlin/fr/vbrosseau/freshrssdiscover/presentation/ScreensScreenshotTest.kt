package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginFailure
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginScreen
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginUiState
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

    @Test
    fun loginScreenEmpty() {
        capture("connexion-vide") { login(LoginUiState()) }
    }

    @Test
    fun loginScreenFilled() {
        capture("connexion-remplie") {
            login(
                LoginUiState(
                    serverAddress = "rss.exemple.org",
                    username = "alice",
                    apiPassword = "mot-de-passe-api",
                    canSubmit = true,
                ),
            )
        }
    }

    /**
     * Le message le plus long de l'écran, sur l'avertissement le plus long.
     *
     * C'est le cas qui déborde : si une mise en page casse, c'est ici que cela
     * se voit — et une assertion textuelle ne le montrerait pas.
     */
    @Test
    fun loginScreenWithTheLongestFailure() {
        capture("connexion-erreur") {
            login(
                LoginUiState(
                    serverAddress = "http://rss.exemple.org",
                    username = "alice",
                    showsInsecureWarning = true,
                    failure = LoginFailure.Server(AuthError.ApiDisabled),
                ),
            )
        }
    }

    @Test
    fun loginScreenConnecting() {
        capture("connexion-en-cours") {
            login(
                LoginUiState(
                    serverAddress = "rss.exemple.org",
                    username = "alice",
                    apiPassword = "mot-de-passe-api",
                    isSubmitting = true,
                ),
            )
        }
    }

    @Composable
    private fun login(uiState: LoginUiState) {
        LoginScreen(
            uiState = uiState,
            onServerAddressChange = {},
            onUsernameChange = {},
            onApiPasswordChange = {},
            onSubmit = {},
        )
    }
}
