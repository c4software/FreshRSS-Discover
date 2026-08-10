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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/*
 * Locale pinned to French, like the screenshot harness (ARCHITECTURE.md §8.2).
 *
 * These cases assert literal labels, and the UI is bilingual since
 * GOAL-021-T02: French lives in `values-fr/`, English in `values/`. Without
 * this qualifier, Robolectric renders the default language (English) and every
 * assertion fails.
 *
 * The content of `values/` is covered elsewhere by a dedicated `en-rUS` case
 * (`EnglishStringsTest`): without it, a string missed in translation would
 * only show on an English-locale device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
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
        // The API password is the first cause of rejection and its existence
        // is not obvious: mentioning it only after a failure would be too late.
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
        // The message must contain the fix, not just the diagnosis.
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
        // The technical message is neither translated nor understandable; it
        // belongs in the logs.
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
        // An API password is typically pasted from a manager: being able to
        // check what was pasted avoids a failure with an invisible cause.
        //
        // The masking itself cannot be asserted through text: a
        // `VisualTransformation` only changes the rendering, and semantics
        // still expose the raw value. What is verified is the button's
        // announced state, the description a screen reader speaks.
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
