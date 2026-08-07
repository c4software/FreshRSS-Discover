package fr.vbrosseau.freshrssdiscover.presentation.login

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        uiState: LoginUiState,
        onSubmit: () -> Unit = {},
        onApiPasswordChange: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            LoginScreen(
                uiState = uiState,
                onServerAddressChange = {},
                onUsernameChange = {},
                onApiPasswordChange = onApiPasswordChange,
                onSubmit = onSubmit,
            )
        }
    }

    @Test
    fun theApiPasswordIsExplainedBeforeAnyFailure() {
        // C'est la première cause de refus, et son existence n'est pas
        // évidente : attendre l'échec pour la mentionner serait trop tard.
        show(LoginUiState())

        composeRule.onNodeWithText("Profil → Mot de passe API", substring = true).assertExists()
    }

    @Test
    fun anIncompleteFormCannotBeSubmitted() {
        show(LoginUiState(canSubmit = false))

        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun aCompleteFormCanBeSubmitted() {
        var submitted = 0
        show(LoginUiState(canSubmit = true), onSubmit = { submitted++ })

        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsEnabled().performClick()

        assertEquals(1, submitted)
    }

    @Test
    fun anInsecureAddressIsFlagged() {
        show(LoginUiState(serverAddress = "http://exemple.org", showsInsecureWarning = true))

        composeRule.onNodeWithTag(LoginTestTags.INSECURE_WARNING).assertExists()
    }

    @Test
    fun nothingIsFlaggedForAnHttpsAddress() {
        show(LoginUiState(serverAddress = "https://exemple.org"))

        composeRule.onNodeWithTag(LoginTestTags.INSECURE_WARNING).assertDoesNotExist()
    }

    @Test
    fun aDisabledApiTellsWhereToEnableIt() {
        // Le message doit contenir le geste, pas seulement le constat.
        show(LoginUiState(failure = LoginFailure.Server(AuthError.ApiDisabled)))

        composeRule.onNodeWithTag(LoginTestTags.FAILURE).assertExists()
        composeRule.onNodeWithText("Autoriser l'accès par API", substring = true).assertExists()
    }

    @Test
    fun refusedCredentialsPointAtTheApiPasswordSpecifically() {
        show(LoginUiState(failure = LoginFailure.Server(AuthError.InvalidCredentials)))

        composeRule.onNodeWithText("mot de passe API", substring = true).assertExists()
    }

    @Test
    fun aStrippedAuthorizationHeaderBlamesTheServerNotTheUser() {
        show(LoginUiState(failure = LoginFailure.Server(AuthError.AuthorizationHeaderNotForwarded)))

        composeRule.onNodeWithText("reverse-proxy", substring = true).assertExists()
    }

    @Test
    fun anUnexpectedFailureNeverLeaksItsTechnicalMessage() {
        // Il n'est ni traduit ni compréhensible : sa place est dans les
        // journaux.
        show(LoginUiState(failure = LoginFailure.Server(AuthError.Unexpected("SSLHandshakeException"))))

        composeRule.onNodeWithText("SSLHandshakeException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(LoginTestTags.FAILURE).assertExists()
    }

    @Test
    fun noFailureCardIsShownWhenNothingHasFailed() {
        show(LoginUiState())

        composeRule.onNodeWithTag(LoginTestTags.FAILURE).assertDoesNotExist()
    }

    @Test
    fun theFormIsLockedAndAnnouncedWhileConnecting() {
        show(LoginUiState(isSubmitting = true, canSubmit = false))

        composeRule.onNodeWithTag(LoginTestTags.PROGRESS).assertExists()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoginTestTags.SERVER_FIELD).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).assertIsNotEnabled()
    }

    @Test
    fun theApiPasswordCanBeRevealedAndMaskedAgain() {
        // Un mot de passe API se recopie depuis un gestionnaire : pouvoir
        // vérifier ce qui a été collé évite un échec dont la cause resterait
        // invisible.
        //
        // Le masquage lui-même n'est pas assertable par le texte : une
        // `VisualTransformation` ne change que le rendu, et la sémantique
        // continue d'exposer la valeur brute. C'est donc l'état annoncé du
        // bouton — sa description, celle que lit un lecteur d'écran — qui est
        // vérifié ici.
        show(LoginUiState(apiPassword = "s3cr3t"))

        composeRule.onNodeWithContentDescription("Afficher le mot de passe").assertExists()

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_VISIBILITY).performClick()
        composeRule.onNodeWithContentDescription("Masquer le mot de passe").assertExists()

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_VISIBILITY).performClick()
        composeRule.onNodeWithContentDescription("Afficher le mot de passe").assertExists()
    }

    @Test
    fun typingInThePasswordFieldReachesTheCaller() {
        val typed = StringBuilder()
        show(LoginUiState(), onApiPasswordChange = { typed.append(it) })

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("a")

        assertEquals("a", typed.toString())
    }
}
