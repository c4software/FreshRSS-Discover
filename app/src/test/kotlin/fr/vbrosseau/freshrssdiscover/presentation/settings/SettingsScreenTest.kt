package fr.vbrosseau.freshrssdiscover.presentation.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = SettingsAccount(serverAddress = "https://rss.exemple.org", username = "alice")

    private fun show(
        uiState: SettingsUiState,
        onSignOutRequest: () -> Unit = {},
        onSignOutConfirm: () -> Unit = {},
        onSignOutDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onSignOutRequest = onSignOutRequest,
                onSignOutConfirm = onSignOutConfirm,
                onSignOutDismiss = onSignOutDismiss,
            )
        }
    }

    @Test
    fun theConnectedServerAndUsernameAreDisplayed() {
        show(SettingsUiState(account = account))

        composeRule.onNodeWithTag(SettingsTestTags.SERVER_ADDRESS).assertTextEquals("https://rss.exemple.org")
        composeRule.onNodeWithTag(SettingsTestTags.USERNAME).assertTextEquals("alice")
    }

    @Test
    fun withoutASessionNothingIsOfferedToSignOutFrom() {
        show(SettingsUiState(account = null))

        composeRule.onNodeWithTag(SettingsTestTags.NO_SESSION).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT).assertDoesNotExist()
    }

    @Test
    fun theAutomaticReadingThresholdsAreDisplayed() {
        show(SettingsUiState(account = account, visibleFractionPercent = 60, continuousVisibilitySeconds = 1))

        composeRule.onNodeWithTag(SettingsTestTags.VISIBLE_FRACTION).assertTextEquals("au moins 60 %")
        composeRule.onNodeWithTag(SettingsTestTags.CONTINUOUS_VISIBILITY).assertTextEquals("au moins 1 s")
    }

    /**
     * Le bouton est annoncé mais inerte : la purge n'est pas encore écrite, et
     * un bouton qui ne fait rien sans le dire serait pire que son absence.
     */
    @Test
    fun theManualPurgeIsAnnouncedButNotYetAvailable() {
        show(SettingsUiState(account = account))

        composeRule.onNodeWithTag(SettingsTestTags.PURGE_CACHE).assertIsNotEnabled()
    }

    @Test
    fun theApplicationVersionAndLicenseAreDisplayed() {
        show(SettingsUiState(account = account, appVersion = "0.1.0"))

        composeRule.onNodeWithTag(SettingsTestTags.APP_VERSION).assertTextEquals("0.1.0")
        composeRule.onNodeWithTag(SettingsTestTags.LICENSE).assertExists()
    }

    @Test
    fun signingOutAsksForConfirmationBeforeAnythingHappens() {
        var requested = 0
        var confirmed = 0
        show(
            SettingsUiState(account = account),
            onSignOutRequest = { requested++ },
            onSignOutConfirm = { confirmed++ },
        )

        // La colonne défile : le bouton de déconnexion ferme la liste et peut
        // se trouver hors écran, où un clic ne l'atteindrait pas.
        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()

        assertEquals(1, requested)
        assertEquals(0, confirmed)
    }

    @Test
    fun theConfirmationIsShownWhenTheStateAsksForIt() {
        show(SettingsUiState(account = account, isSignOutConfirmationVisible = true))

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_DIALOG).assertExists()
    }

    @Test
    fun confirmingTheDialogReportsTheSignOut() {
        var confirmed = 0
        var dismissed = 0
        show(
            SettingsUiState(account = account, isSignOutConfirmationVisible = true),
            onSignOutConfirm = { confirmed++ },
            onSignOutDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_CONFIRM).performClick()

        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun cancellingTheDialogReportsNoSignOut() {
        var confirmed = 0
        var dismissed = 0
        show(
            SettingsUiState(account = account, isSignOutConfirmationVisible = true),
            onSignOutConfirm = { confirmed++ },
            onSignOutDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(SettingsTestTags.SIGN_OUT_CANCEL).performClick()

        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }
}
