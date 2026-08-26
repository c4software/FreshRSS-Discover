package fr.vbrosseau.freshrssdiscover.presentation.subscriptions

import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.subscription.FakeSubscriptionRepository
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionError
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionId
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val xkcd = Subscription(SubscriptionId(3L), "XKCD", "https://xkcd.com/atom.xml")
    private val repository = FakeSubscriptionRepository(listOf(xkcd))

    /** Built lazily: `init` loads on `Dispatchers.Main`, substituted by the rule first. */
    private val viewModel: SubscriptionsViewModel by lazy { SubscriptionsViewModel(repository) }

    private val state get() = viewModel.uiState.value

    // ----- Listing -----------------------------------------------------------

    @Test
    fun theListIsReadOnOpening() = runTest {
        assertEquals(listOf(xkcd), state.subscriptions)
        assertFalse(state.isLoading)
        assertNull(state.loadFailure)
    }

    @Test
    fun aFailedListingIsReportedWithARetryPossible() = runTest {
        repository.nextFailure = SubscriptionError.ServerUnreachable

        assertEquals(R.string.subscriptions_error_server_unreachable, state.loadFailure)
        assertNull(state.subscriptions)

        viewModel.load()

        assertEquals(listOf(xkcd), state.subscriptions)
        assertNull(state.loadFailure)
    }

    // ----- Adding ------------------------------------------------------------

    @Test
    fun anEmptyDraftIsRefusedBeforeAnythingIsSent() = runTest {
        viewModel.onAdd()

        assertEquals(R.string.subscriptions_error_blank, state.notice)
        assertEquals(emptyList(), repository.subscribedUrls)
    }

    @Test
    fun anAddressThatIsNotOneIsRefusedBeforeAnythingIsSent() = runTest {
        viewModel.onDraftChange("pas une adresse")
        viewModel.onAdd()

        assertEquals(R.string.subscriptions_error_invalid, state.notice)
        assertEquals("pas une adresse", state.draftUrl)
        assertEquals(emptyList(), repository.subscribedUrls)
    }

    @Test
    fun aValidAddressIsSentNormalisedThenTheListIsReRead() = runTest {
        viewModel.onDraftChange("exemple.org/rss")
        viewModel.onAdd()

        assertEquals("https://exemple.org/rss", repository.subscribedUrls.single().value)
        assertEquals("", state.draftUrl)
        assertEquals(R.string.subscriptions_added, state.notice)
        assertFalse(state.isSubmitting)
        // Re-read, not patched: the server names and orders the new feed.
        assertEquals(2, repository.listCallCount)
        assertEquals(2, state.subscriptions?.size)
    }

    @Test
    fun aRefusedAdditionKeepsTheDraftAndTheList() = runTest {
        // The initial listing must have happened first, or it would eat the failure.
        assertEquals(listOf(xkcd), state.subscriptions)
        repository.nextFailure = SubscriptionError.Rejected
        viewModel.onDraftChange("https://exemple.org/pas-un-flux")
        viewModel.onAdd()

        assertEquals(R.string.subscriptions_error_rejected, state.notice)
        assertEquals("https://exemple.org/pas-un-flux", state.draftUrl)
        assertEquals(listOf(xkcd), state.subscriptions)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun typingAgainClearsTheNotice() = runTest {
        viewModel.onAdd()
        viewModel.onDraftChange("x")

        assertNull(state.notice)
    }

    // ----- Removing ----------------------------------------------------------

    @Test
    fun removalAsksForConfirmationFirst() = runTest {
        viewModel.onRemoveRequest(xkcd)

        assertEquals(xkcd, state.removalCandidate)
        assertEquals(emptyList(), repository.unsubscribedIds)

        viewModel.onRemoveDismiss()

        assertNull(state.removalCandidate)
        assertEquals(emptyList(), repository.unsubscribedIds)
    }

    @Test
    fun aConfirmedRemovalIsSentThenTheListIsReRead() = runTest {
        viewModel.onDraftChange("en cours")
        viewModel.onRemoveRequest(xkcd)
        viewModel.onRemoveConfirm()

        assertEquals(listOf(xkcd.id), repository.unsubscribedIds)
        assertNull(state.removalCandidate)
        assertEquals(R.string.subscriptions_removed, state.notice)
        assertEquals(emptyList(), state.subscriptions)
        // A removal has nothing to do with what is being typed.
        assertEquals("en cours", state.draftUrl)
    }

    @Test
    fun aFailedRemovalIsReportedAndTheListStays() = runTest {
        // The initial listing must have happened first, or it would eat the failure.
        assertEquals(listOf(xkcd), state.subscriptions)
        repository.nextFailure = SubscriptionError.NoNetwork
        viewModel.onRemoveRequest(xkcd)
        viewModel.onRemoveConfirm()

        assertEquals(R.string.subscriptions_error_no_network, state.notice)
        assertEquals(listOf(xkcd), state.subscriptions)
    }

    @Test
    fun confirmingWithoutACandidateDoesNothing() = runTest {
        viewModel.onRemoveConfirm()

        assertEquals(emptyList(), repository.unsubscribedIds)
        assertNull(state.notice)
    }

    @Test
    fun anExpiredSessionIsWordedAsSuch() = runTest {
        // The initial listing must have happened first, or it would eat the failure.
        assertEquals(listOf(xkcd), state.subscriptions)
        repository.nextFailure = SubscriptionError.SessionExpired
        viewModel.onDraftChange("https://exemple.org/rss")
        viewModel.onAdd()

        assertEquals(R.string.subscriptions_error_session_expired, state.notice)
        assertTrue(state.subscriptions?.isNotEmpty() == true)
    }
}
