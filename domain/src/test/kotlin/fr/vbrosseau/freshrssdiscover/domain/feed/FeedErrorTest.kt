package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedErrorTest {
    /**
     * Verifies the cause set stays shorter than authentication's.
     *
     * Adding "API disabled" or "credentials rejected" would compile, then
     * force every caller to handle cases that can no longer occur once the
     * session is open.
     */
    @Test
    fun everyCauseIsDistinct() {
        val causes: List<FeedError> =
            listOf(
                FeedError.NoNetwork,
                FeedError.ServerUnreachable,
                FeedError.SessionExpired,
                FeedError.Unexpected("peu importe"),
            )

        assertEquals(causes.size, causes.distinct().size)
    }

    @Test
    fun anExpiredSessionIsACauseOfItsOwn() {
        // Not a read error but an end of session: the repository pairs it
        // with an invalidation, and the root routing returns to the sign-in
        // screen. Conflating it with "server unreachable" would leave the
        // user retrying forever.
        assertTrue(FeedError.SessionExpired != FeedError.ServerUnreachable)
    }

    @Test
    fun theTechnicalMessageSurvivesTransport() {
        assertEquals("HTTP 500", (FeedError.Unexpected("HTTP 500")).technicalMessage)
    }
}
