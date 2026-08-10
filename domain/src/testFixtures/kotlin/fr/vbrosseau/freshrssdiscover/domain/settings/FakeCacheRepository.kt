package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory cache for tests.
 *
 * Preserves the real repository's invariant: purging removes only purgeable
 * articles and leaves the rest. A fake that emptied everything would let
 * through a ViewModel promising the screen a complete deletion.
 */
class FakeCacheRepository(
    initial: CacheStatus = CacheStatus.Empty,
) : CacheRepository {
    private val status = MutableStateFlow(initial)

    /** Number of purges requested, to verify a gesture triggers exactly one. */
    var purgeCount: Int = 0
        private set

    /** Current state, to verify a purge's effect without collecting. */
    val current: CacheStatus
        get() = status.value

    override fun observeCacheStatus(): StateFlow<CacheStatus> = status

    override suspend fun purgeReadArticles(): Int {
        purgeCount++
        val removed = status.value.purgeableCount
        status.value =
            status.value.copy(
                articleCount = status.value.articleCount - removed,
                purgeableCount = 0,
            )
        return removed
    }
}
