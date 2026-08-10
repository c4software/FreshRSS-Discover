package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory feed freshness for tests.
 *
 * The acknowledgement is shared, as in the real implementation: this lets a
 * test verify that a notice acknowledged in List mode stays silent in Swipe
 * mode, by handing the same instance to both ViewModels.
 */
class FakeFeedFreshnessRepository(
    initial: FeedFreshness = FeedFreshness(),
) : FeedFreshnessRepository {
    private val freshness = MutableStateFlow(initial)

    /** Instant that [recordRefresh] writes. Set by the test. */
    var nowEpochMillis: Long = 0L

    var recordCallCount: Int = 0
        private set

    var acknowledgeCallCount: Int = 0
        private set

    val current: FeedFreshness get() = freshness.value

    override fun observeFreshness(): Flow<FeedFreshness> = freshness

    override suspend fun recordRefresh() {
        recordCallCount++
        freshness.value = freshness.value.copy(lastRefreshEpochMillis = nowEpochMillis)
    }

    override suspend fun acknowledgeStale() {
        acknowledgeCallCount++
        val latest = freshness.value
        freshness.value = latest.copy(acknowledgedRefreshEpochMillis = latest.lastRefreshEpochMillis)
    }

    /** Sets a state directly, bypassing the write path. */
    fun set(value: FeedFreshness) {
        freshness.value = value
    }
}
