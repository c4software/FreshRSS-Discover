package fr.vbrosseau.freshrssdiscover.domain.subscription

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome

/**
 * In-memory subscriptions for tests.
 *
 * Behaves like the server as far as the screen can tell: an addition
 * appears in the next listing with a fresh identifier, a removal disappears
 * from it, and an unknown identifier is refused. [nextFailure] scripts one
 * failure for the next call, then clears itself — the property the screen
 * relies on is that a retry after a failure can succeed.
 */
class FakeSubscriptionRepository(
    initial: List<Subscription> = emptyList(),
) : SubscriptionRepository {
    private val subscriptions = initial.toMutableList()

    /** Failure returned by the next call, whichever it is; consumed by that call. */
    var nextFailure: SubscriptionError? = null

    val subscribedUrls: MutableList<FeedUrl> = mutableListOf()

    val unsubscribedIds: MutableList<SubscriptionId> = mutableListOf()

    var listCallCount: Int = 0
        private set

    /** What a listing would return now, without counting as a call. */
    val current: List<Subscription>
        get() = subscriptions.toList()

    override suspend fun list(): SubscriptionResult<List<Subscription>> {
        listCallCount++
        return consumeFailure() ?: Outcome.Success(subscriptions.toList())
    }

    override suspend fun subscribe(url: FeedUrl): SubscriptionResult<Unit> {
        subscribedUrls += url
        consumeFailure()?.let { return it }
        val id = SubscriptionId((subscriptions.maxOfOrNull { it.id.value } ?: 0L) + 1L)
        subscriptions += Subscription(id = id, title = url.value, url = url.value)
        return Outcome.Success(Unit)
    }

    override suspend fun unsubscribe(id: SubscriptionId): SubscriptionResult<Unit> {
        unsubscribedIds += id
        consumeFailure()?.let { return it }
        return if (subscriptions.removeIf { it.id == id }) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(SubscriptionError.Rejected)
        }
    }

    private fun consumeFailure(): Outcome.Failure<SubscriptionError>? =
        nextFailure?.let { failure ->
            nextFailure = null
            Outcome.Failure(failure)
        }
}
