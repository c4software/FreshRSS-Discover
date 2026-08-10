package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The responses here are literal: they reproduce what
 * `https://demo.freshrss.org/` actually returned (docs/freshrss-api.md §1
 * and §2), not what a reading of the source suggested.
 */
class FreshRssApiTest {
    private val address = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val credentials = Credentials("alice", "mot-de-passe-api")

    private var lastRequest: HttpRequestData? = null

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        FreshRssApi(
            createFreshRssHttpClient(
                MockEngine { request ->
                    lastRequest = request
                    handler(request)
                },
            ),
        )

    private fun MockRequestHandleScope.text(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8"),
        )

    // ----- Recognition probe -------------------------------------------------

    @Test
    fun theProbeAcceptsTheTwoLetterBody() = runTest {
        // Observed: the Content-Type is text/html, not text/plain. Trusting
        // the MIME type would reject a perfectly valid instance.
        val outcome = api {
            respond("OK", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
        }.probe(address)

        assertIs<ApiOutcome.Success<Unit>>(outcome)
    }

    @Test
    fun theProbeTargetsTheApiEndpointWithoutAnyQueryString() = runTest {
        // Observed: any query string makes the server answer 400 instead of
        // OK. A probe adding one would fail on every server.
        api { text("OK") }.probe(address)

        assertEquals("https://exemple.org/api/greader.php", lastRequest?.url.toString())
        assertEquals("", lastRequest?.url?.encodedQuery)
    }

    @Test
    fun aServerAnsweringSomethingElseIsReportedAsMalformedNotAsAnHttpError() = runTest {
        // Real case: captive portal, maintenance page, proxy answering 200
        // to anything. Treating it as an HTTP error would show a false
        // diagnosis.
        val outcome = api {
            respond("<html>Bienvenue</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }.probe(address)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aHostThatIsNotFreshRssSurfacesItsHttpStatus() = runTest {
        val outcome = api { respondError(HttpStatusCode.NotFound) }.probe(address)

        assertEquals(HttpStatusCode.NotFound.value, assertIs<ApiOutcome.HttpError>(outcome).status)
    }

    // ----- Header-forwarding probe -------------------------------------------

    @Test
    fun theCompatibilityProbeSendsAnAuthorizationHeaderOfItsOwn() = runTest {
        // Observed: without a header in the request, the probe answers FAIL
        // even on a correctly configured server. It checks the presence of
        // the header it receives, not its validity.
        api { text("PASS") }.checkAuthorizationForwarding(address)

        assertTrue(lastRequest?.headers?.contains(HttpHeaders.Authorization) == true)
    }

    @Test
    fun theCompatibilityProbeReadsTheBodyNotTheStatus() = runTest {
        // Observed: the status is 200 in both cases. Testing the HTTP code
        // would verify nothing.
        val failing = api { text("FAIL get HTTP Authorization header! Wrong Web server configuration.") }
            .checkAuthorizationForwarding(address)

        assertFalse(assertIs<ApiOutcome.Success<Boolean>>(failing).value)

        val passing = api { text("PASS") }.checkAuthorizationForwarding(address)
        assertTrue(assertIs<ApiOutcome.Success<Boolean>>(passing).value)
    }

    // ----- ClientLogin -------------------------------------------------------

    @Test
    fun clientLoginExtractsTheAuthLineAndIgnoresTheOthers() = runTest {
        val outcome = api {
            text("SID=alice/c0ffee\nLSID=null\nAuth=alice/c0ffee\n")
        }.clientLogin(address, credentials)

        // `SID` carries the same value and `LSID` is `null`: only `Auth`
        // matters, and selecting it by key rather than by position protects
        // against a change in line order.
        assertEquals("alice/c0ffee", assertIs<ApiOutcome.Success<AuthToken>>(outcome).value.value)
    }

    @Test
    fun clientLoginPostsTheCredentialsRatherThanPuttingThemInTheUrl() = runTest {
        // FreshRSS also accepts GET but then logs a warning: the password
        // would appear in the server logs.
        api { text("Auth=alice/c0ffee") }.clientLogin(address, credentials)

        assertEquals(HttpMethod.Post, lastRequest?.method)
        assertEquals(
            "https://exemple.org/api/greader.php/accounts/ClientLogin",
            lastRequest?.url.toString(),
        )
        assertFalse("mot-de-passe-api" in lastRequest?.url.toString())
    }

    @Test
    fun clientLoginToleratesAResponseWithoutTrailingNewline() = runTest {
        val outcome = api { text("Auth=alice/c0ffee") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.Success<*>>(outcome)
    }

    @Test
    fun aRefusedLoginSurfacesItsStatusAndPlainTextBody() = runTest {
        // Observed: unknown username and wrong password both answer 401
        // "Unauthorized!". The API layer does not distinguish them; they are
        // indistinguishable.
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }
            .clientLogin(address, credentials)

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.Unauthorized.value, error.status)
        assertEquals("Unauthorized!", error.body)
    }

    @Test
    fun aSyntacticallyInvalidUsernameSurfacesA400() = runTest {
        val outcome = api { text("Bad Request!", HttpStatusCode.BadRequest) }
            .clientLogin(address, Credentials("nom invalide!", "x"))

        assertEquals(HttpStatusCode.BadRequest.value, assertIs<ApiOutcome.HttpError>(outcome).status)
    }

    @Test
    fun aSuccessfulStatusWithoutAnAuthLineIsMalformed() = runTest {
        // A captive portal answering 200 to anything would land here.
        // Mistaking it for success would produce an empty token, then an
        // inexplicable 401 on the next call.
        val outcome = api { text("SID=alice/c0ffee\nLSID=null\n") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun anEmptyAuthValueIsMalformed() = runTest {
        val outcome = api { text("Auth=\n") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aTruncatedResponseIsMalformedRatherThanAnError() = runTest {
        val outcome = api { text("Au") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aJsonResponseWhereTextWasExpectedIsMalformed() = runTest {
        // A misconfigured server, or an interposed API gateway.
        val outcome = api {
            respond(
                ByteReadChannel("""{"error":"nope"}"""),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    // ----- Transport ---------------------------------------------------------

    @Test
    fun aNetworkFailureIsReportedRatherThanThrown() = runTest {
        // Otherwise every caller would have to know Ktor's exceptions.
        val outcome = api { throw IOException("hôte inconnu") }.probe(address)

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    @Test
    fun clientLoginAlsoReportsANetworkFailure() = runTest {
        val outcome = api { throw IOException("délai dépassé") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.TransportError>(outcome)
    }
}
