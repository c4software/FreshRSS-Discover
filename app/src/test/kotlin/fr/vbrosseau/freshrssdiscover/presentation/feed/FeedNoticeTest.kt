package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private const val ACTION_TAG = "notice:action"
private const val DISMISS_TAG = "notice:dismiss"

/**
 * La bandelette du flux, éprouvée seule.
 *
 * Les deux modes en posaient chacun une copie ; elle vit désormais ici, et
 * c'est ici qu'on vérifie ce qu'ils tenaient tous les deux pour acquis — une
 * cible tactile assez grande, et une seconde commande facultative.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class FeedNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        onAction: () -> Unit = {},
        dismissLabel: String? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                FeedNotice(
                    message = "Ce flux date de plusieurs heures.",
                    actionLabel = "Rafraîchir",
                    onAction = onAction,
                    actionModifier = Modifier.testTag(ACTION_TAG),
                    dismissLabel = dismissLabel,
                    onDismiss = onDismiss,
                    dismissModifier = Modifier.testTag(DISMISS_TAG),
                )
            }
        }
    }

    @Test
    fun theMessageIsShown() {
        show()

        composeRule.onNodeWithText("Ce flux date de plusieurs heures.").assertIsDisplayed()
    }

    @Test
    fun theActionReportsItsPress() {
        var presses = 0
        show(onAction = { presses++ })

        composeRule.onNodeWithTag(ACTION_TAG).performClick()

        assertEquals(1, presses)
    }

    @Test
    fun withoutASecondCommandNoneIsShown() {
        show()

        composeRule.onNodeWithTag(DISMISS_TAG).assertDoesNotExist()
    }

    @Test
    fun theSecondCommandReportsItsPress() {
        var dismissals = 0
        show(dismissLabel = "Fermer", onDismiss = { dismissals++ })

        composeRule.onNodeWithTag(DISMISS_TAG).performClick()

        assertEquals(1, dismissals)
    }

    @Test
    fun bothCommandsAreLargeEnoughToTouch() {
        // SPECS.md §7.1 : 48 dp, quelle que soit la hauteur du libellé.
        show(dismissLabel = "Fermer", onDismiss = {})

        composeRule.onNodeWithTag(ACTION_TAG).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(DISMISS_TAG).assertHeightIsAtLeast(48.dp)
    }
}
