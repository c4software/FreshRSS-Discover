package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of contact with the Google Reader-compatible FreshRSS API.
 *
 * Everything specific to this API — paths, headers, response shapes, tokens —
 * stops here (ARCHITECTURE.md §2.1). None of it must appear above this layer.
 *
 * No method throws: failures are reported through [ApiOutcome]. An exception
 * crossing this layer would force every caller to know Ktor's exceptions.
 */
@Singleton
internal class FreshRssApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    /**
     * Verifies that the address designates a FreshRSS instance.
     *
     * The only reliable discriminant is the `OK` body returned by a bare `GET`
     * on the entry point (docs/freshrss-api.md §1.1). Two observed pitfalls:
     * the `Content-Type` is `text/html`, and any query string at all makes the
     * server answer `400` instead of `OK`.
     *
     * This probe runs before any login attempt: without it, a typo in the
     * address would produce a `401` the user would blame on their password.
     */
    suspend fun probe(address: ServerAddress): ApiOutcome<Unit> = call {
        val response = httpClient.get(address.apiEndpoint)
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            response.bodyAsText().trim() == PROBE_RESPONSE -> ApiOutcome.Success(Unit)
            else -> ApiOutcome.MalformedResponse("la racine de l'API n'a pas répondu « $PROBE_RESPONSE »")
        }
    }

    /**
     * Verifies that the web server forwards the `Authorization` header.
     *
     * Some reverse proxies strip it; every login would then fail with `401`,
     * wrongly blaming the user's credentials.
     *
     * Two observed particularities without which the probe is worthless: the
     * status is always `200` — the verdict is in the body — and the request
     * itself must carry an `Authorization` header, even a fake one, since it
     * is its presence on reception that is checked.
     */
    suspend fun checkAuthorizationForwarding(address: ServerAddress): ApiOutcome<Boolean> = call {
        val response = httpClient.get(address.apiEndpoint + COMPATIBILITY_PATH) {
            header(HttpHeaders.Authorization, "GoogleLogin auth=$COMPATIBILITY_PROBE_TOKEN")
        }
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            else -> ApiOutcome.Success(response.bodyAsText().trim().startsWith(COMPATIBILITY_PASS))
        }
    }

    /**
     * Opens a session.
     *
     * The expected password is the API password, distinct from the login
     * password. It is sent via `POST`: FreshRSS also accepts `GET`, but then
     * logs a warning — the password would appear in the server logs.
     *
     * The response is plain text, one `key=value` pair per line. Only `Auth`
     * is kept; `SID` carries the same value and `LSID` is `null`.
     */
    suspend fun clientLogin(address: ServerAddress, credentials: Credentials): ApiOutcome<AuthToken> = call {
        val response = httpClient.submitForm(
            url = address.apiEndpoint + CLIENT_LOGIN_PATH,
            formParameters = parameters {
                append("Email", credentials.username)
                append("Passwd", credentials.apiPassword)
            },
        )
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            else -> tokenFrom(response)
        }
    }

    /**
     * Fetches one page of the reading list.
     *
     * Three precautions, each drawn from observation (docs/freshrss-api.md §3.4
     * and §3.5):
     *
     * - the `Authorization` header is mandatory here, unlike `ClientLogin`:
     *   without it the server answers `401`;
     * - the `c` parameter is added only when a cursor exists. An empty or
     *   non-numeric `c` is silently reset to the start of the stream: the
     *   request succeeds and returns the first page again. Sending an empty
     *   `c` would therefore produce a silent infinite loop, never an error;
     * - the body is deserialized manually rather than via `response.body()`:
     *   truncated JSON must become [ApiOutcome.MalformedResponse], not an
     *   exception every caller would have to catch.
     *
     * Already-read articles are always excluded, via `xt`: the Discover feed
     * only shows unread items (SPECS.md §4.1), and no caller has ever asked
     * for anything else.
     */
    suspend fun streamContents(
        address: ServerAddress,
        token: AuthToken,
        pageSize: Int,
        cursor: PageCursor? = null,
    ): ApiOutcome<StreamContentsDto> = call {
        val response = httpClient.get(address.apiEndpoint + STREAM_CONTENTS_PATH) {
            header(HttpHeaders.Authorization, "$AUTHORIZATION_SCHEME${token.value}")
            parameter(PARAM_COUNT, pageSize)
            cursor?.let { parameter(PARAM_CONTINUATION, it.value) }
            parameter(PARAM_EXCLUDE_TARGET, READ_STATE)
        }
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            else -> streamContentsFrom(response.bodyAsText())
        }
    }

    /**
     * Fetches the token required by every modifying operation.
     *
     * The response is plain text — a 57-character digest padded with `Z`,
     * followed by a newline (docs/freshrss-api.md §2.3). That length is
     * observed, not contractual: checking it here would make the application
     * fail on a perfectly valid token the day FreshRSS changes its shape. A
     * rejected token signals itself with a `401` when used, never by its size.
     * Only an empty body is rejected: it could only produce a silently useless
     * `edit-tag`.
     *
     * The token is deterministic and reusable: the caller obtains it once and
     * keeps it, re-requesting it after a `401` if needed.
     */
    suspend fun modificationToken(address: ServerAddress, token: AuthToken): ApiOutcome<ModificationToken> = call {
        val response = httpClient.get(address.apiEndpoint + TOKEN_PATH) {
            header(HttpHeaders.Authorization, "$AUTHORIZATION_SCHEME${token.value}")
        }
        val body = response.bodyAsText().trim()
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            body.isEmpty() -> ApiOutcome.MalformedResponse("le jeton de modification est vide")
            else -> ApiOutcome.Success(ModificationToken(body))
        }
    }

    /**
     * Marks a batch of articles as read.
     *
     * Batching is what makes the feature viable: a Discover feed marks
     * articles as the user scrolls, and one request per visible article would
     * saturate the network for nothing (SPECS.md §4.5).
     *
     * The body is a form (docs/freshrss-api.md §4.1): `T` carries the
     * modification token, `a` the state to add, and `i` is repeated once per
     * article.
     *
     * A successful response is the text `OK`, not JSON, and gives no
     * per-article report — an identifier unknown to the server produces no
     * error.
     */
    suspend fun markAsRead(
        address: ServerAddress,
        token: AuthToken,
        modificationToken: ModificationToken,
        articleIds: List<ArticleId>,
    ): ApiOutcome<Unit> = call {
        val response = httpClient.submitForm(
            url = address.apiEndpoint + EDIT_TAG_PATH,
            formParameters = parameters {
                append(PARAM_MODIFICATION_TOKEN, modificationToken.value)
                append(PARAM_ADD_STATE, READ_STATE)
                articleIds.forEach { append(PARAM_ITEM, it.toUnsignedDecimal()) }
            },
        ) {
            header(HttpHeaders.Authorization, "$AUTHORIZATION_SCHEME${token.value}")
        }
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            response.bodyAsText().trim() == EDIT_TAG_RESPONSE -> ApiOutcome.Success(Unit)
            else -> ApiOutcome.MalformedResponse("edit-tag n'a pas répondu « $EDIT_TAG_RESPONSE »")
        }
    }

    /**
     * FreshRSS error bodies are plain text; an unreadable `2xx` is therefore a
     * malformed body, not a transport failure.
     */
    private fun streamContentsFrom(body: String): ApiOutcome<StreamContentsDto> = try {
        ApiOutcome.Success(FreshRssJson.decodeFromString(StreamContentsDto.serializer(), body))
    } catch (@Suppress("SwallowedException") failure: SerializationException) {
        ApiOutcome.MalformedResponse("réponse de stream/contents illisible : ${failure.message}")
    }

    private suspend fun tokenFrom(response: HttpResponse): ApiOutcome<AuthToken> {
        val auth = response.bodyAsText()
            .lineSequence()
            .mapNotNull { line -> line.trim().takeIf { it.startsWith(AUTH_PREFIX) } }
            .map { it.removePrefix(AUTH_PREFIX).trim() }
            .firstOrNull { it.isNotEmpty() }

        return when (auth) {
            null -> ApiOutcome.MalformedResponse("aucune ligne « ${AUTH_PREFIX}… » dans la réponse")
            else -> ApiOutcome.Success(AuthToken(auth))
        }
    }

    /**
     * Maps every transport exception to [ApiOutcome.TransportError].
     *
     * `CancellationException` must keep propagating: catching it would let a
     * coroutine outlive the cancellation of its scope, while the screen
     * awaiting it is already gone.
     */
    private suspend fun <T> call(block: suspend () -> ApiOutcome<T>): ApiOutcome<T> = try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        ApiOutcome.TransportError(failure)
    }

    private companion object {
        const val CLIENT_LOGIN_PATH = "/accounts/ClientLogin"
        const val COMPATIBILITY_PATH = "/check/compatibility"
        const val STREAM_CONTENTS_PATH = "/reader/api/0/stream/contents/reading-list"
        const val TOKEN_PATH = "/reader/api/0/token"
        const val EDIT_TAG_PATH = "/reader/api/0/edit-tag"
        const val PARAM_MODIFICATION_TOKEN = "T"
        const val PARAM_ADD_STATE = "a"
        const val PARAM_ITEM = "i"
        const val EDIT_TAG_RESPONSE = "OK"
        const val AUTH_PREFIX = "Auth="
        const val AUTHORIZATION_SCHEME = "GoogleLogin auth="
        const val PARAM_COUNT = "n"
        const val PARAM_CONTINUATION = "c"
        const val PARAM_EXCLUDE_TARGET = "xt"

        /** The only state used here: `xt` excludes it, leaving only unread items. */
        const val READ_STATE = "user/-/state/com.google/read"
        const val PROBE_RESPONSE = "OK"
        const val COMPATIBILITY_PASS = "PASS"

        /**
         * Fake token for the compatibility probe: it checks the header's
         * presence, never its validity.
         */
        const val COMPATIBILITY_PROBE_TOKEN = "x/y"
    }
}

/**
 * Renders the identifier as FreshRSS expects it: unsigned decimal.
 *
 * Article identifiers are unsigned 64-bit integers, while Kotlin's `Long` is
 * signed. Past `Long.MAX_VALUE`, the value is kept bit-for-bit but reads as
 * negative: `toString()` would send e.g. `-1` where the server expects
 * `18446744073709551615`. The mark would then target a nonexistent article,
 * and `edit-tag` would answer `OK` without doing anything
 * (docs/freshrss-api.md §4.1), making the loss completely silent.
 */
private fun ArticleId.toUnsignedDecimal(): String = java.lang.Long.toUnsignedString(value)
