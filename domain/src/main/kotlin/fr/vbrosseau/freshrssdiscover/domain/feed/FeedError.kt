package fr.vbrosseau.freshrssdiscover.domain.feed

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome

/**
 * Failure causes when fetching articles.
 *
 * Deliberately shorter than `AuthError`: authentication causes (API disabled,
 * host that is not FreshRSS, rejected credentials) cannot occur once the
 * session is open. Reusing them would force every caller to handle impossible
 * cases.
 */
sealed interface FeedError {
    /** No connectivity: the request never left the device. */
    data object NoNetwork : FeedError

    /** The server does not respond: DNS, timeout, TLS refused. */
    data object ServerUnreachable : FeedError

    /**
     * The server rejected the token.
     *
     * Happens without notice when the user changes their API password. This is
     * not a read error but the end of the session: the repository pairs it
     * with an invalidation, and the root gate returns to the sign-in screen by
     * itself (SPECS.md §3.4).
     */
    data object SessionExpired : FeedError

    /**
     * Failure not described by any of the cases above.
     *
     * [technicalMessage] goes to logs, never to display.
     */
    data class Unexpected(val technicalMessage: String) : FeedError
}

/** Result of a feed read. */
typealias FeedResult<T> = Outcome<T, FeedError>
