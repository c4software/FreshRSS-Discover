package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SPECS.md §3.3 requires one message per cause. This mapping is the only
 * place that decides which: getting it wrong shows a false diagnosis and
 * sends the user searching in the wrong direction.
 */
class AuthErrorMappingTest {
    @Test
    fun anUnreachableServerIsDistinguishedFromAnAbsentNetwork() {
        // The HTTP stack reports both identically, yet the fixes are
        // unrelated: wait for the network, or fix the address. Only observed
        // connectivity tells them apart.
        val failure = ApiOutcome.TransportError(IOException("hôte inconnu"))

        assertEquals(AuthError.ServerUnreachable, failure.toAuthError(isOnline = true))
        assertEquals(AuthError.NoNetwork, failure.toAuthError(isOnline = false))
    }

    @Test
    fun aWellFormedButNonFreshRssResponseNamesTheRightCause() {
        // Captive portal, maintenance page, proxy answering 200 to anything.
        val outcome = ApiOutcome.MalformedResponse("la racine n'a pas répondu « OK »")

        assertEquals(AuthError.NotAFreshRssServer, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a401MeansCredentialsWhicheverOfTheTwoIsWrong() {
        // Observed: unknown username and wrong password both answer 401.
        // Distinguishing them would allow account enumeration.
        val outcome = ApiOutcome.HttpError(401, "Unauthorized!")

        assertEquals(AuthError.InvalidCredentials, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a400AlsoSendsTheUserBackToHisCredentials() {
        // 400 means a syntactically invalid username. The cause differs from
        // 401, but the expected user action is the same.
        val outcome = ApiOutcome.HttpError(400, "Bad Request!")

        assertEquals(AuthError.InvalidCredentials, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a503MeansTheApiIsDisabledNotThatTheCredentialsAreWrong() {
        // A checkbox in the FreshRSS admin. Confusing it with rejected
        // credentials would have the user re-checking their password
        // indefinitely.
        val outcome = ApiOutcome.HttpError(503, "Service Unavailable!")

        assertEquals(AuthError.ApiDisabled, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a404DesignatesTheHostNotThePath() {
        // Observed: an unknown path under the API answers 401, never 404. A
        // 404 can thus only come from a host that is not FreshRSS.
        val outcome = ApiOutcome.HttpError(404, "Not Found")

        assertEquals(AuthError.NotAFreshRssServer, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun anUnhandledStatusKeepsItsCodeForTheLogs() {
        val outcome = ApiOutcome.HttpError(500, "Internal Server Error")

        val error = assertIs<AuthError.Unexpected>(outcome.toAuthError(isOnline = true))
        assertTrue("500" in error.technicalMessage)
    }

    @Test
    fun a501IsReportedAsUnexpectedBecauseItIsAProgrammingFault() {
        // 501 means output other than JSON where JSON is required: no user
        // action would change anything.
        val outcome = ApiOutcome.HttpError(501, "Not Implemented!")

        assertIs<AuthError.Unexpected>(outcome.toAuthError(isOnline = true))
    }

    @Test
    fun aLongErrorBodyIsTruncatedBeforeReachingTheLogs() {
        // A captive portal returns an entire HTML page: dumping it into the
        // logs would make them unreadable.
        val outcome = ApiOutcome.HttpError(500, "x".repeat(10_000))

        val error = assertIs<AuthError.Unexpected>(outcome.toAuthError(isOnline = true))
        assertTrue(error.technicalMessage.length < 300, "message de ${error.technicalMessage.length} caractères")
    }

    @Test
    fun theConnectivityOnlyMattersForTransportFailures() {
        // A server that answered proves the network works: letting
        // connectivity influence an HTTP status would produce inconsistent
        // diagnoses.
        val outcome = ApiOutcome.HttpError(503, "Service Unavailable!")

        assertEquals(outcome.toAuthError(isOnline = true), outcome.toAuthError(isOnline = false))
    }

    @Test
    fun mappingASuccessIsReportedAsAProgrammingFault() {
        // Should never happen; reporting it beats returning a plausible
        // error that would mask the anomaly.
        val error = ApiOutcome.Success(Unit).toAuthError(isOnline = true)

        assertIs<AuthError.Unexpected>(error)
    }
}
