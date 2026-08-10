package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Authentication failure causes, as the user must be able to distinguish them.
 *
 * A single "sign-in failed" type would make SPECS.md §3.3 inapplicable: the
 * spec requires one message per cause, because the corrective actions differ.
 * Checking the API password and enabling the API in the server admin are
 * unrelated fixes.
 *
 * The enumeration is deliberately closed: the `data` layer must translate
 * every technical failure into one of these cases, and the compiler enforces
 * exhaustiveness.
 */
sealed interface AuthError {
    /** No connectivity: the request never left the device. */
    data object NoNetwork : AuthError

    /** The address does not respond: DNS, closed port, timeout, TLS refused. */
    data object ServerUnreachable : AuthError

    /**
     * The address responds, but it is not a FreshRSS instance.
     *
     * Common real-world case: the user enters their server address without the
     * sub-path where FreshRSS is installed.
     */
    data object NotAFreshRssServer : AuthError

    /**
     * The API is disabled on the server.
     *
     * FreshRSS then responds `503` on every endpoint. This is a checkbox in
     * the server admin, not a credentials problem; the message must say so,
     * otherwise the user will keep re-checking their password.
     */
    data object ApiDisabled : AuthError

    /**
     * Username or API password rejected.
     *
     * The displayed message must ask for the API password, not the login
     * password (SPECS.md §3.2).
     */
    data object InvalidCredentials : AuthError

    /**
     * Credentials are valid, but the web server does not forward the
     * `Authorization` header to FreshRSS.
     *
     * Some reverse proxies strip it. Without this distinct case, sign-in would
     * succeed and then every subsequent call would fail with `401`: the user
     * would see "invalid credentials" despite correct ones. The fix lives in
     * the server configuration, not in the app.
     */
    data object AuthorizationHeaderNotForwarded : AuthError

    /**
     * Failure not described by any of the cases above.
     *
     * [technicalMessage] is for logs only, never for display: it is neither
     * translated nor user-readable. It must not contain any secret.
     */
    data class Unexpected(val technicalMessage: String) : AuthError
}
