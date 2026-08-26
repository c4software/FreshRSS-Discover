package fr.vbrosseau.freshrssdiscover.domain.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SubscriptionErrorTest {
    @Test
    fun everyCauseIsDistinct() {
        val causes: List<SubscriptionError> =
            listOf(
                SubscriptionError.NoNetwork,
                SubscriptionError.ServerUnreachable,
                SubscriptionError.SessionExpired,
                SubscriptionError.Rejected,
                SubscriptionError.Unexpected("peu importe"),
            )

        assertEquals(causes.size, causes.distinct().size)
    }

    @Test
    fun aRefusalIsNotAnUnexpectedFailure() {
        // A 400 is the user's to fix — the address is not a feed — while an
        // unexpected failure is ours to log. Retrying the same address after
        // a refusal is pointless; the screen must be able to say so.
        assertNotEquals<SubscriptionError>(SubscriptionError.Rejected, SubscriptionError.Unexpected("HTTP 400"))
    }

    @Test
    fun theTechnicalMessageSurvivesTransport() {
        assertEquals("HTTP 500", SubscriptionError.Unexpected("HTTP 500").technicalMessage)
    }

    @Test
    fun aSubscriptionCarriesItsIdentifierTitleAndAddress() {
        val subscription = Subscription(SubscriptionId(12L), "XKCD", "https://xkcd.com/atom.xml")

        assertEquals(12L, subscription.id.value)
        assertEquals("XKCD", subscription.title)
        assertEquals("https://xkcd.com/atom.xml", subscription.url)
    }
}
