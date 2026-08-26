package fr.vbrosseau.freshrssdiscover.presentation.subscriptions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class SubscriptionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val xkcd = Subscription(SubscriptionId(3L), "XKCD", "https://xkcd.com/atom.xml")
    private val monde = Subscription(SubscriptionId(12L), "Le Monde", "https://www.lemonde.fr/rss/une.xml")

    private fun show(
        uiState: SubscriptionsUiState,
        onDraftChange: (String) -> Unit = {},
        onAdd: () -> Unit = {},
        onRetry: () -> Unit = {},
        onRemoveRequest: (Subscription) -> Unit = {},
        onRemoveConfirm: () -> Unit = {},
        onRemoveDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            SubscriptionsScreen(
                uiState = uiState,
                onDraftChange = onDraftChange,
                onAdd = onAdd,
                onRetry = onRetry,
                onRemoveRequest = onRemoveRequest,
                onRemoveConfirm = onRemoveConfirm,
                onRemoveDismiss = onRemoveDismiss,
            )
        }
    }

    @Test
    fun whileLoadingOnlyTheFormAndASpinnerAreShown() {
        show(SubscriptionsUiState())

        composeRule.onNodeWithTag(SubscriptionsTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(SubscriptionsTestTags.URL_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(SubscriptionsTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun eachSubscriptionHasARowWithItsOwnRemoveButton() {
        show(SubscriptionsUiState(subscriptions = listOf(xkcd, monde)))

        composeRule.onNodeWithTag(SubscriptionsTestTags.rowOf(xkcd.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(SubscriptionsTestTags.rowOf(monde.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(SubscriptionsTestTags.removeOf(xkcd.id)).assertIsDisplayed()
        composeRule.onNodeWithTag(SubscriptionsTestTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun anEmptyListSaysSo() {
        show(SubscriptionsUiState(subscriptions = emptyList()))

        composeRule.onNodeWithTag(SubscriptionsTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun aFailedListingShowsTheMessageAndRetries() {
        var retried = false
        show(
            SubscriptionsUiState(loadFailure = R.string.subscriptions_error_no_network),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithTag(SubscriptionsTestTags.FAILURE)
            .assertTextEquals("Pas de réseau. Vérifiez la connexion, puis réessayez.")
        composeRule.onNodeWithTag(SubscriptionsTestTags.RETRY).performClick()

        assertTrue(retried)
    }

    @Test
    fun typingReachesTheDraftAndTheButtonAdds() {
        var draft = ""
        var added = false
        show(
            SubscriptionsUiState(subscriptions = emptyList()),
            onDraftChange = { draft = it },
            onAdd = { added = true },
        )

        composeRule.onNodeWithTag(SubscriptionsTestTags.URL_FIELD).performTextInput("xkcd.com/atom.xml")
        composeRule.onNodeWithTag(SubscriptionsTestTags.ADD).performClick()

        assertEquals("xkcd.com/atom.xml", draft)
        assertTrue(added)
    }

    @Test
    fun theNoticeIsShownUnderTheForm() {
        show(SubscriptionsUiState(subscriptions = listOf(xkcd), notice = R.string.subscriptions_added))

        composeRule.onNodeWithTag(SubscriptionsTestTags.NOTICE).assertTextEquals("Flux ajouté.")
    }

    @Test
    fun whileSubmittingTheFormAndTheBinsAreHeld() {
        show(SubscriptionsUiState(subscriptions = listOf(xkcd), isSubmitting = true))

        composeRule.onNodeWithTag(SubscriptionsTestTags.ADD).assertIsNotEnabled()
        composeRule.onNodeWithTag(SubscriptionsTestTags.URL_FIELD).assertIsNotEnabled()
        composeRule.onNodeWithTag(SubscriptionsTestTags.removeOf(xkcd.id)).assertIsNotEnabled()
    }

    @Test
    fun theBinAsksForTheRowToBeRemoved() {
        var requested: Subscription? = null
        show(SubscriptionsUiState(subscriptions = listOf(xkcd, monde)), onRemoveRequest = { requested = it })

        composeRule.onNodeWithTag(SubscriptionsTestTags.removeOf(monde.id)).assertIsEnabled().performClick()

        assertEquals(monde, requested)
        composeRule.onNodeWithTag(SubscriptionsTestTags.REMOVE_DIALOG).assertDoesNotExist()
    }

    @Test
    fun theConfirmationDialogConfirmsOrDismisses() {
        var confirmed = false
        var dismissed = false
        show(
            SubscriptionsUiState(subscriptions = listOf(xkcd), removalCandidate = xkcd),
            onRemoveConfirm = { confirmed = true },
            onRemoveDismiss = { dismissed = true },
        )

        composeRule.onNodeWithTag(SubscriptionsTestTags.REMOVE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(SubscriptionsTestTags.REMOVE_CONFIRM).performClick()
        assertTrue(confirmed)

        composeRule.onNodeWithTag(SubscriptionsTestTags.REMOVE_CANCEL).performClick()
        assertTrue(dismissed)
    }

    /** SPECS.md §7.1: every target is at least 48 dp. */
    @Test
    fun theButtonsAreBigEnoughToBeTapped() {
        show(SubscriptionsUiState(subscriptions = listOf(xkcd)))

        val add = composeRule.onNodeWithTag(SubscriptionsTestTags.ADD).getUnclippedBoundsInRoot()
        val bin = composeRule.onNodeWithTag(SubscriptionsTestTags.removeOf(xkcd.id)).getUnclippedBoundsInRoot()
        assertTrue(add.height >= 48.dp, "bouton d'ajout : ${add.height}")
        assertTrue(bin.height >= 48.dp, "corbeille : ${bin.height}")
    }
}
