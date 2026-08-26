package fr.vbrosseau.freshrssdiscover.domain.subscription

/**
 * The user's subscriptions on the server (SPECS.md §6).
 *
 * One-shot calls, no cache and no observation: the list only changes through
 * this very contract or in FreshRSS itself, and the screen that shows it
 * re-reads it after each of its own actions. Caching would only add a way
 * for the list to be stale.
 */
interface SubscriptionRepository {
    /** Every subscription, in the server's order. */
    suspend fun list(): SubscriptionResult<List<Subscription>>

    /** The server fetches the address: a non-feed is [SubscriptionError.Rejected]. */
    suspend fun subscribe(url: FeedUrl): SubscriptionResult<Unit>

    suspend fun unsubscribe(id: SubscriptionId): SubscriptionResult<Unit>
}
