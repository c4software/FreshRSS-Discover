package fr.vbrosseau.freshrssdiscover.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Delay beyond which a server is considered unreachable. */
private const val REQUEST_TIMEOUT_MILLIS = 30_000L

/**
 * Establishing the connection must fail faster than processing it: a server
 * that is down shows up here, and making the user wait thirty seconds to learn
 * it would be needlessly long.
 */
private const val CONNECT_TIMEOUT_MILLIS = 10_000L

/**
 * Serializer shared by all API responses.
 *
 * `ignoreUnknownKeys`: FreshRSS adds fields across versions — `frss:priority`
 * is a recent example. Without this tolerance, a server update would be enough
 * to break parsing of every response.
 */
internal val FreshRssJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

/**
 * Builds the HTTP client for the FreshRSS API.
 *
 * The engine is a parameter rather than an internal choice: this lets tests
 * substitute a `MockEngine` and describe literal responses, malformed ones
 * included.
 *
 * @param verboseLogging enable only in debug builds.
 */
internal fun createFreshRssHttpClient(
    engine: HttpClientEngine,
    verboseLogging: Boolean = false,
    logger: Logger = TimberLogger,
): HttpClient = HttpClient(engine) {
    /**
     * No exception on error statuses: FreshRSS status codes are the useful
     * information — `503` means "API disabled", `401` "invalid token"
     * (docs/freshrss-api.md §5). Letting them throw would force reconstructing
     * the code from an exception and handling network and application errors
     * through the same channel.
     */
    expectSuccess = false

    install(ContentNegotiation) {
        /*
         * Registered for `application/json` only — the default, and essential
         * here: FreshRSS error responses are `text/plain`. A broader
         * registration would try to deserialize them, fail, and mask the real
         * HTTP status code.
         */
        json(FreshRssJson)
    }

    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
    }

    if (verboseLogging) {
        install(Logging) {
            this.logger = logger

            // HEADERS rather than BODY: the `ClientLogin` body contains the
            // API password in clear text. Headers are genuinely useful for
            // diagnostics, provided the one carrying the token is masked.
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
    }
}

/** Routes Ktor logging to the application's logger. */
internal object TimberLogger : Logger {
    override fun log(message: String) {
        Timber.tag("FreshRssApi").d(message)
    }
}
