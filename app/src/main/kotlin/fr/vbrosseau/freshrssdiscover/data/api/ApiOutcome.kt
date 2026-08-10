package fr.vbrosseau.freshrssdiscover.data.api

/**
 * Raw outcome of an API call, before any business interpretation.
 *
 * The API layer does not decide what a `401` means for the user: it reports
 * what the server answered. Translation into `AuthError` happens above, in a
 * component that can be tested separately and that must distinguish cases the
 * HTTP status code alone cannot settle (docs/freshrss-api.md §5).
 */
internal sealed interface ApiOutcome<out T> {
    data class Success<T>(val value: T) : ApiOutcome<T>

    /** The server answered with a non-`2xx` status. [body] is raw text. */
    data class HttpError(val status: Int, val body: String) : ApiOutcome<Nothing>

    /**
     * The server answered `2xx`, but the body does not have the expected shape.
     *
     * Real-world cases: a captive portal, a maintenance page, or a proxy that
     * answers `200` to everything. Conflating this with an HTTP error would
     * produce a wrong diagnostic.
     */
    data class MalformedResponse(val detail: String) : ApiOutcome<Nothing>

    /** The request did not complete: DNS, TLS, timeout, no network. */
    data class TransportError(val cause: Throwable) : ApiOutcome<Nothing>
}

/**
 * The only status that several consumers of [ApiOutcome.HttpError] interpret
 * themselves: it signals a rejected token, hence a session to invalidate,
 * whatever the call that receives it.
 */
internal const val HTTP_UNAUTHORIZED = 401
