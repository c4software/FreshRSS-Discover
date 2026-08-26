package fr.vbrosseau.freshrssdiscover.presentation.subscriptions

import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionError
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionsUiStateTest {
    @Test
    fun everyFailureHasAMessageOfItsOwn() {
        val messages = listOf(
            SubscriptionError.NoNetwork,
            SubscriptionError.ServerUnreachable,
            SubscriptionError.SessionExpired,
            SubscriptionError.Rejected,
            SubscriptionError.Unexpected("HTTP 500"),
        ).map(::messageOf)

        assertEquals(messages.size, messages.distinct().size)
        assertEquals(R.string.subscriptions_error_rejected, messageOf(SubscriptionError.Rejected))
    }

    @Test
    fun loadingIsTheAbsenceOfBothAListAndAFailure() {
        assertTrue(SubscriptionsUiState().isLoading)
        assertFalse(SubscriptionsUiState(subscriptions = emptyList()).isLoading)
        assertFalse(SubscriptionsUiState(loadFailure = R.string.subscriptions_error_no_network).isLoading)
    }
}
