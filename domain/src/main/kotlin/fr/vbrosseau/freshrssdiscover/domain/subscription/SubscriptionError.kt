package fr.vbrosseau.freshrssdiscover.domain.subscription

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome

/**
 * Failure causes when managing subscriptions.
 *
 * The feed's causes, plus one: the server may **refuse** what it was asked —
 * an address that is not a feed, a feed already subscribed, an identifier
 * it no longer knows. That refusal is the user's to fix, unlike the others,
 * and it is the only case where retrying the same thing is pointless.
 */
sealed interface SubscriptionError {
    /** No connectivity: the request never left the device. */
    data object NoNetwork : SubscriptionError

    /** The server does not respond: DNS, timeout, TLS refused. */
    data object ServerUnreachable : SubscriptionError

    /** The server rejected the token; the session is being closed (SPECS.md §3.4). */
    data object SessionExpired : SubscriptionError

    /**
     * The server understood and refused — `400`, with nothing else said
     * (docs/freshrss-api.md §4.3). On an addition, the address is not a feed
     * the server could fetch, or is already subscribed.
     */
    data object Rejected : SubscriptionError

    /** Failure not described by any of the cases above; [technicalMessage] goes to logs. */
    data class Unexpected(val technicalMessage: String) : SubscriptionError
}

/** Result of a subscription operation. */
typealias SubscriptionResult<T> = Outcome<T, SubscriptionError>
