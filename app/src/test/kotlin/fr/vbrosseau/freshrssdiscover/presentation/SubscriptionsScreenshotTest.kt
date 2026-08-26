package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionId
import fr.vbrosseau.freshrssdiscover.presentation.subscriptions.SubscriptionsScreen
import fr.vbrosseau.freshrssdiscover.presentation.subscriptions.SubscriptionsUiState
import org.junit.Test

/**
 * Visual references for the feeds screen (SPECS.md §6).
 *
 * The notice is the only primary-colored line under a form, and the load
 * failure the only `error`-colored text next to an outlined button: both
 * are where a dark-theme contrast defect would settle unseen.
 */
class SubscriptionsScreenshotTest : ScreenshotTest() {

    @Test
    fun subscriptionsScreenWithFeeds() {
        capture("flux") {
            subscriptions(SubscriptionsUiState(subscriptions = FEEDS))
        }
    }

    @Test
    fun subscriptionsScreenAfterAnAddition() {
        capture("flux-ajoute") {
            subscriptions(SubscriptionsUiState(subscriptions = FEEDS, notice = R.string.subscriptions_added))
        }
    }

    @Test
    fun subscriptionsScreenWithARefusedAddress() {
        capture("flux-refuse") {
            subscriptions(
                SubscriptionsUiState(
                    subscriptions = FEEDS,
                    draftUrl = "https://exemple.org/pas-un-flux",
                    notice = R.string.subscriptions_error_rejected,
                ),
            )
        }
    }

    @Test
    fun subscriptionsScreenEmpty() {
        capture("flux-vide") {
            subscriptions(SubscriptionsUiState(subscriptions = emptyList()))
        }
    }

    @Test
    fun subscriptionsScreenWhenTheListingFailed() {
        capture("flux-erreur") {
            subscriptions(SubscriptionsUiState(loadFailure = R.string.subscriptions_error_server_unreachable))
        }
    }

    @Test
    fun subscriptionsScreenConfirmingARemoval() {
        capture("flux-retrait") {
            subscriptions(SubscriptionsUiState(subscriptions = FEEDS, removalCandidate = FEEDS.first()))
        }
    }

    @Composable
    private fun subscriptions(uiState: SubscriptionsUiState) {
        SubscriptionsScreen(
            uiState = uiState,
            onDraftChange = {},
            onAdd = {},
            onRetry = {},
            onRemoveRequest = {},
            onRemoveConfirm = {},
            onRemoveDismiss = {},
        )
    }

    private companion object {
        /** A long address: where a row can push its bin off the edge. */
        val FEEDS = listOf(
            Subscription(SubscriptionId(12L), "Le Monde — À la une", "https://www.lemonde.fr/rss/une.xml"),
            Subscription(SubscriptionId(3L), "XKCD", "https://xkcd.com/atom.xml"),
            Subscription(
                SubscriptionId(27L),
                "Android Developers Blog",
                "https://feeds.feedburner.com/blogspot/hsDu?format=xml&very=long&query=string",
            ),
        )
    }
}
