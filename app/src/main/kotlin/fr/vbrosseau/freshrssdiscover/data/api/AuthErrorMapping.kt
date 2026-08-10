package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError

/**
 * Translates a technical outcome into a diagnosable cause for the user.
 *
 * This is the only place in the project where an HTTP status code takes on
 * meaning. Above this layer, only [AuthError] is used (ARCHITECTURE.md §7).
 *
 * @param isOnline connectivity observed at the time of failure. It is the only
 *   way to distinguish "no network" from "server unreachable", which the HTTP
 *   stack reports identically, and whose fixes differ: wait for the network,
 *   or correct the address.
 */
internal fun ApiOutcome<*>.toAuthError(isOnline: Boolean): AuthError = when (this) {
    is ApiOutcome.Success -> AuthError.Unexpected("toAuthError appelé sur un succès")

    is ApiOutcome.TransportError ->
        if (isOnline) AuthError.ServerUnreachable else AuthError.NoNetwork

    /*
     * The server answered `2xx` but the body does not have the expected shape:
     * captive portal, maintenance page, proxy answering 200 to everything.
     * From the user's point of view, the address does not designate a FreshRSS
     * instance, which is exactly what they must fix.
     */
    is ApiOutcome.MalformedResponse -> AuthError.NotAFreshRssServer

    is ApiOutcome.HttpError -> httpStatusToAuthError(status, body)
}

private fun httpStatusToAuthError(status: Int, body: String): AuthError = when (status) {
    /*
     * Observed: an unknown username and a wrong password both answer `401`.
     * Distinguishing them would allow account enumeration, which FreshRSS
     * rightly refuses, so the message must cover both hypotheses.
     *
     * A `401` on a nonexistent path also lands here: authorization is checked
     * before routing. This is harmless, as the recognition probe has already
     * ruled that case out.
     */
    HTTP_UNAUTHORIZED -> AuthError.InvalidCredentials

    /*
     * `400` means a syntactically invalid username: empty, whitespace, `../`.
     * Not a password error, but from the user's point of view the fix is the
     * same: re-enter the credentials.
     */
    HTTP_BAD_REQUEST -> AuthError.InvalidCredentials

    /** A checkbox in the admin panel, not a credentials problem. */
    HTTP_SERVICE_UNAVAILABLE -> AuthError.ApiDisabled

    /*
     * Observed: an unknown path *under* the API answers `401`, never `404`.
     * A `404` therefore designates the host: wrong address, or an installation
     * in an unspecified subdirectory.
     */
    HTTP_NOT_FOUND -> AuthError.NotAFreshRssServer

    else -> AuthError.Unexpected("HTTP $status: ${body.take(MAX_TECHNICAL_MESSAGE_LENGTH)}")
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404
private const val HTTP_SERVICE_UNAVAILABLE = 503

/**
 * The error body goes to the logs: truncating it prevents a full HTML page,
 * typically a captive portal's, from being dumped there.
 */
private const val MAX_TECHNICAL_MESSAGE_LENGTH = 200
