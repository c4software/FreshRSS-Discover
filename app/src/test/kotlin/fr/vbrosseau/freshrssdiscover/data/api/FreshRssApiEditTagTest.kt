package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
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
 * The responses here are literal: they reproduce the form observed on a real
 * instance (docs/freshrss-api.md §2.3 and §4.1). In particular, `edit-tag`
 * answers `OK` as plain text, never JSON.
 */
class FreshRssApiEditTagTest {
    private val address = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val token = AuthToken("alice/c0ffee")
    private val modificationToken = ModificationToken("Z".repeat(TOKEN_LENGTH))

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

    /** The request body is a form: it, not the URL, carries the fields. */
    private fun formParameters(): Parameters = (lastRequest?.body as FormDataContent).formData

    // ----- Modification token ------------------------------------------------

    @Test
    fun theModificationTokenIsReadFromThePlainTextBody() = runTest {
        val body = "8Zqp4wA9yTfM".padEnd(TOKEN_LENGTH, 'Z')

        val outcome = api { text("$body\n") }.modificationToken(address, token)

        assertEquals(body, assertIs<ApiOutcome.Success<ModificationToken>>(outcome).value.value)
    }

    @Test
    fun theModificationTokenRequestCarriesTheSessionTokenAndTargetsTheTokenEndpoint() = runTest {
        api { text("Z".repeat(TOKEN_LENGTH)) }.modificationToken(address, token)

        assertEquals("GoogleLogin auth=alice/c0ffee", lastRequest?.headers?.get(HttpHeaders.Authorization))
        assertTrue(lastRequest?.url?.encodedPath?.endsWith("/reader/api/0/token") == true)
    }

    @Test
    fun aTokenOfAnUnexpectedLengthIsStillAccepted() = runTest {
        // The 57 characters are an observation, not a contract. Requiring
        // them would break the app on a perfectly valid token if FreshRSS
        // changed its shape; a rejected token shows up as a 401 on use
        // (docs/freshrss-api.md §2.3).
        val outcome = api { text("court") }.modificationToken(address, token)

        assertEquals("court", assertIs<ApiOutcome.Success<ModificationToken>>(outcome).value.value)
    }

    @Test
    fun anEmptyTokenBodyIsMalformed() = runTest {
        // An empty token would only produce a silently useless edit-tag.
        val outcome = api { text("\n") }.modificationToken(address, token)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun anExpiredSessionRefusesTheModificationToken() = runTest {
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }.modificationToken(address, token)

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.Unauthorized.value, error.status)
        assertEquals("Unauthorized!", error.body)
    }

    @Test
    fun aNetworkFailureOnTheTokenIsReportedRatherThanThrown() = runTest {
        val outcome = api { throw IOException("hôte inconnu") }.modificationToken(address, token)

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    // ----- Mark as read ------------------------------------------------------

    @Test
    fun aBatchIsSentAsASingleRequestWithOneItemFieldPerArticle() = runTest {
        // Batching is this endpoint's reason for being: one request per
        // visible article would saturate the network (SPECS.md §4.5).
        api { text("OK") }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L), ArticleId(2L), ArticleId(3L)))

        val form = formParameters()
        assertTrue(lastRequest?.url?.encodedPath?.endsWith("/reader/api/0/edit-tag") == true)
        assertEquals(listOf("1", "2", "3"), form.getAll("i"))
        assertEquals(modificationToken.value, form["T"])
        assertEquals("user/-/state/com.google/read", form["a"])
        assertEquals("GoogleLogin auth=alice/c0ffee", lastRequest?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun anIdentifierBeyondTheSignedRangeIsSentUnsigned() = runTest {
        // Identifiers are unsigned 64-bit integers; beyond Long.MAX_VALUE
        // they read negative in Kotlin. `toString()` would send "-1": the
        // server would mark a nonexistent article and answer OK without
        // doing anything, a silent loss.
        api { text("OK") }.markAsRead(address, token, modificationToken, listOf(ArticleId(-1L), ArticleId(-2L)))

        assertEquals(listOf("18446744073709551615", "18446744073709551614"), formParameters().getAll("i"))
    }

    @Test
    fun anOkBodySignalsSuccess() = runTest {
        val outcome = api { text("OK\n") }.markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        assertIs<ApiOutcome.Success<Unit>>(outcome)
    }

    @Test
    fun anExpiredModificationTokenSurfacesItsStatusAndPlainTextBody() = runTest {
        // This 401 is what tells the upper layer to request a fresh token,
        // then to treat persistent failure as a lost session.
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.Unauthorized.value, error.status)
        assertEquals("Unauthorized!", error.body)
    }

    @Test
    fun aBodyOtherThanOkIsMalformed() = runTest {
        // Captive portal or maintenance page answering 200 to anything: the
        // mark was not applied, and the queue must keep it.
        val outcome = api { json("""{"status":"queued"}""") }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aNetworkFailureOnTheMarkIsReportedRatherThanThrown() = runTest {
        val outcome = api { throw IOException("hôte inconnu") }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    private companion object {
        /** Observed token length (docs/freshrss-api.md §2.3), used only by the fixtures. */
        const val TOKEN_LENGTH = 57
    }
}
