package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The bodies are literal: `subscription/list` as docs/freshrss-api.md §3.1
 * records it, `subscription/edit` answering `OK` as plain text (§4.3).
 */
class FreshRssSubscriptionApiTest {
    private val address = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val token = AuthToken("alice/c0ffee")

    private var lastRequest: HttpRequestData? = null

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        FreshRssSubscriptionApi(
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

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8"),
        )

    private fun formParameters(): Parameters = (lastRequest?.body as FormDataContent).formData

    // ----- List --------------------------------------------------------------

    @Test
    fun theSubscriptionsAreReadWithTheirStreamIdTitleAndUrl() = runTest {
        val body = """
            {"subscriptions":[
              {"id":"feed/12","title":"Le Monde","categories":[{"id":"user/-/label/Presse","label":"Presse"}],
               "url":"https://www.lemonde.fr/rss/une.xml","htmlUrl":"https://www.lemonde.fr/","iconUrl":"https://exemple.org/f.php?1"},
              {"id":"feed/3","title":"XKCD","categories":[],"url":"https://xkcd.com/atom.xml","htmlUrl":"https://xkcd.com/","iconUrl":""}
            ]}
        """.trimIndent()

        val outcome = api { json(body) }.list(address, token)

        val subscriptions = assertIs<ApiOutcome.Success<SubscriptionListDto>>(outcome).value.subscriptions
        assertEquals(
            listOf(
                SubscriptionDto(id = "feed/12", title = "Le Monde", url = "https://www.lemonde.fr/rss/une.xml"),
                SubscriptionDto(id = "feed/3", title = "XKCD", url = "https://xkcd.com/atom.xml"),
            ),
            subscriptions,
        )
    }

    @Test
    fun theListRequestAsksForJsonAndCarriesTheSessionToken() = runTest {
        // `output=json` is mandatory: anything else answers 501 (§3.1).
        api { json("""{"subscriptions":[]}""") }.list(address, token)

        assertEquals(HttpMethod.Get, lastRequest?.method)
        assertTrue(lastRequest?.url?.encodedPath?.endsWith("/reader/api/0/subscription/list") == true)
        assertEquals("json", lastRequest?.url?.parameters?.get("output"))
        assertEquals("GoogleLogin auth=alice/c0ffee", lastRequest?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun anEmptyListIsASuccessWithNoSubscription() = runTest {
        val outcome = api { json("""{"subscriptions":[]}""") }.list(address, token)

        assertEquals(emptyList(), assertIs<ApiOutcome.Success<SubscriptionListDto>>(outcome).value.subscriptions)
    }

    @Test
    fun aTruncatedListIsMalformedRatherThanThrown() = runTest {
        val outcome = api { json("""{"subscriptions":[{"id":"feed/12","ti""") }.list(address, token)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aPlainTextListIsMalformed() = runTest {
        // A maintenance page or a captive portal answering 200.
        val outcome = api { text("<html>maintenance</html>") }.list(address, token)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun anExpiredSessionRefusesTheList() = runTest {
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }.list(address, token)

        assertEquals(HttpStatusCode.Unauthorized.value, assertIs<ApiOutcome.HttpError>(outcome).status)
    }

    @Test
    fun aNetworkFailureOnTheListIsReportedRatherThanThrown() = runTest {
        val outcome = api { throw IOException("hôte inconnu") }.list(address, token)

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    // ----- Subscribe ---------------------------------------------------------

    @Test
    fun subscribingPostsTheFeedUrlAsAStreamNameWithoutAnyModificationToken() = runTest {
        // No `T`: the dispatcher does not check the token on this path,
        // unlike `edit-tag` (§4.3). Sending one anyway would be harmless but
        // would document a requirement that does not exist.
        val outcome = api { text("OK") }.subscribe(address, token, "https://xkcd.com/atom.xml")

        assertIs<ApiOutcome.Success<Unit>>(outcome)
        assertEquals(HttpMethod.Post, lastRequest?.method)
        assertTrue(lastRequest?.url?.encodedPath?.endsWith("/reader/api/0/subscription/edit") == true)
        val form = formParameters()
        assertEquals("feed/https://xkcd.com/atom.xml", form["s"])
        assertEquals("subscribe", form["ac"])
        assertEquals(null, form["T"])
        assertEquals("GoogleLogin auth=alice/c0ffee", lastRequest?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun anAddressThatIsNotAFeedIsRefusedWithABadRequest() = runTest {
        // The server fetched the address and found no feed: `400`, plain text.
        val outcome = api { text("Bad Request!", HttpStatusCode.BadRequest) }
            .subscribe(address, token, "https://exemple.org/pas-un-flux")

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.BadRequest.value, error.status)
        assertEquals("Bad Request!", error.body)
    }

    @Test
    fun aSubscribeAnsweredWithSomethingOtherThanOkIsMalformed() = runTest {
        val outcome = api { json("""{"numResults":1}""") }.subscribe(address, token, "https://xkcd.com/atom.xml")

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun theOkAcknowledgementToleratesATrailingNewline() = runTest {
        val outcome = api { text("OK\n") }.subscribe(address, token, "https://xkcd.com/atom.xml")

        assertIs<ApiOutcome.Success<Unit>>(outcome)
    }

    // ----- Unsubscribe -------------------------------------------------------

    @Test
    fun unsubscribingPostsTheListedStreamId() = runTest {
        val outcome = api { text("OK") }.unsubscribe(address, token, "feed/12")

        assertIs<ApiOutcome.Success<Unit>>(outcome)
        val form = formParameters()
        assertEquals("feed/12", form["s"])
        assertEquals("unsubscribe", form["ac"])
        assertEquals(null, form["T"])
    }

    @Test
    fun anUnknownFeedIsRefusedWithABadRequest() = runTest {
        val outcome = api { text("Bad Request!", HttpStatusCode.BadRequest) }.unsubscribe(address, token, "feed/999")

        assertEquals(HttpStatusCode.BadRequest.value, assertIs<ApiOutcome.HttpError>(outcome).status)
    }

    @Test
    fun aNetworkFailureOnAnEditIsReportedRatherThanThrown() = runTest {
        val outcome = api { throw IOException("délai dépassé") }.unsubscribe(address, token, "feed/12")

        assertIs<ApiOutcome.TransportError>(outcome)
    }
}
