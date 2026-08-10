package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The responses here are literal: they reproduce the form observed on a real
 * instance (docs/freshrss-api.md §3.4 and §3.5), not what a reading of the
 * source suggested.
 */
class FreshRssApiStreamTest {
    private val address = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val token = AuthToken("alice/c0ffee")

    private var lastRequest: HttpRequestData? = null

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        FreshRssApi(
            createFreshRssHttpClient(
                MockEngine { request ->
                    lastRequest = request
                    handler(request)
                },
            ),
        )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8"),
        )

    private fun MockRequestHandleScope.text(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8"),
        )

    private fun queryParameter(name: String): String? = lastRequest?.url?.parameters?.get(name)

    // ----- Request -----------------------------------------------------------

    @Test
    fun theRequestCarriesTheTokenInTheAuthorizationHeader() = runTest {
        // Unlike ClientLogin, this endpoint requires the header: without it
        // the server answers 401 regardless of the rest.
        api { json(PAGE_WITH_CONTINUATION) }.streamContents(address, token, pageSize = 40)

        assertEquals(
            "GoogleLogin auth=alice/c0ffee",
            lastRequest?.headers?.get(HttpHeaders.Authorization),
        )
    }

    @Test
    fun theRequestTargetsTheReadingListAndAsksForThePageSize() = runTest {
        api { json(PAGE_WITH_CONTINUATION) }.streamContents(address, token, pageSize = 40)

        assertTrue(
            lastRequest?.url?.encodedPath?.endsWith("/reader/api/0/stream/contents/reading-list") == true,
        )
        assertEquals("40", queryParameter("n"))
    }

    @Test
    fun theFirstPageIsRequestedWithoutAnyContinuationParameter() = runTest {
        // Observed: an empty or non-numeric `c` is silently reset to the
        // start of the feed. The server then answers 200 with the first
        // page, never an error, so sending an empty one would loop on the
        // same page indefinitely without any signal.
        api { json(PAGE_WITH_CONTINUATION) }.streamContents(address, token, pageSize = 40)

        assertNull(queryParameter("c"))
    }

    @Test
    fun aCursorIsSentAsTheContinuationParameter() = runTest {
        api { json(PAGE_WITH_CONTINUATION) }
            .streamContents(address, token, pageSize = 40, cursor = PageCursor("45219"))

        assertEquals("45219", queryParameter("c"))
    }

    @Test
    fun everyPageRequestExcludesTheArticlesAlreadyRead() = runTest {
        // The Discover feed shows only unread articles (SPECS.md §4.1): `xt`
        // is systematic. Omitting it would bring back half the feed.
        api { json(PAGE_WITH_CONTINUATION) }
            .streamContents(address, token, pageSize = 40)

        assertEquals("user/-/state/com.google/read", queryParameter("xt"))
    }

    // ----- Response ----------------------------------------------------------

    @Test
    fun aValidPageIsDeserialisedWithItsItemsAndItsContinuation() = runTest {
        val outcome = api { json(PAGE_WITH_CONTINUATION) }.streamContents(address, token, pageSize = 2)

        val page = assertIs<ApiOutcome.Success<StreamContentsDto>>(outcome).value
        assertEquals(2, page.items.size)
        assertEquals("45219", page.continuation)
    }

    @Test
    fun anAbsentContinuationEndsTheFeed() = runTest {
        // The only end-of-feed signal: the API returns no total count. A
        // full page without a cursor is a legitimate end.
        val outcome = api { json(LAST_PAGE) }.streamContents(address, token, pageSize = 2)

        assertNull(assertIs<ApiOutcome.Success<StreamContentsDto>>(outcome).value.continuation)
    }

    @Test
    fun anExpiredSessionSurfacesItsStatusAndPlainTextBody() = runTest {
        // Observed: the error body is plain text, never JSON. This 401 is
        // what signals a session to renew higher up.
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }
            .streamContents(address, token, pageSize = 40)

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.Unauthorized.value, error.status)
        assertEquals("Unauthorized!", error.body)
    }

    @Test
    fun aTruncatedJsonBodyIsMalformedRatherThanThrown() = runTest {
        // Real case: connection cut mid-response. Letting the
        // deserialization exception propagate would force every caller to
        // know about it.
        val outcome = api { json("""{"id":"user/-/state/com.google/reading-list","items":[{"id":""") }
            .streamContents(address, token, pageSize = 40)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aPlainTextBodyWhereJsonWasExpectedIsMalformed() = runTest {
        // Captive portal, maintenance page, or proxy answering 200 to anything.
        val outcome = api { text("Service temporairement indisponible") }
            .streamContents(address, token, pageSize = 40)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    // ----- Transport ---------------------------------------------------------

    @Test
    fun aNetworkFailureIsReportedRatherThanThrown() = runTest {
        val outcome = api { throw IOException("hôte inconnu") }.streamContents(address, token, pageSize = 40)

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    private companion object {
        val PAGE_WITH_CONTINUATION = """
            {
              "id": "user/-/state/com.google/reading-list",
              "updated": 1700000000,
              "items": [
                {
                  "id": "tag:google.com,2005:reader/item/00000000000b0b1f",
                  "timestampUsec": "1700000000000000",
                  "published": 1699999000,
                  "title": "Premier article",
                  "canonical": [{ "href": "https://exemple.org/1" }],
                  "categories": ["user/-/state/com.google/reading-list"],
                  "origin": { "streamId": "feed/12", "title": "Flux" },
                  "summary": { "content": "<p>Un</p>" }
                },
                {
                  "id": "tag:google.com,2005:reader/item/00000000000b0b20",
                  "timestampUsec": "1700000001000000",
                  "published": 1699999001,
                  "title": "Second article",
                  "canonical": [{ "href": "https://exemple.org/2" }],
                  "categories": ["user/-/state/com.google/reading-list"],
                  "origin": { "streamId": "feed/12", "title": "Flux" },
                  "summary": { "content": "<p>Deux</p>" }
                }
              ],
              "continuation": "45219"
            }
        """.trimIndent()

        val LAST_PAGE = """
            {
              "id": "user/-/state/com.google/reading-list",
              "updated": 1700000000,
              "items": [
                {
                  "id": "tag:google.com,2005:reader/item/00000000000b0b1f",
                  "title": "Dernier article",
                  "origin": { "streamId": "feed/12", "title": "Flux" }
                }
              ]
            }
        """.trimIndent()
    }
}
