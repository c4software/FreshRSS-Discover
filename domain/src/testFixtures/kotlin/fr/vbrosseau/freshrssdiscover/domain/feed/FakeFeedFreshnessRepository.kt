package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fraîcheur du flux en mémoire, pour les tests.
 *
 * L'acquittement y est **partagé**, comme dans l'implémentation réelle : c'est
 * ce qui permet à un test de vérifier qu'un avis acquitté en mode Liste reste
 * muet en mode Balayage, en donnant le même exemplaire aux deux ViewModels.
 */
class FakeFeedFreshnessRepository(
    initial: FeedFreshness = FeedFreshness(),
) : FeedFreshnessRepository {
    private val freshness = MutableStateFlow(initial)

    /** Instant que [recordRefresh] écrira. Le test le pose lui-même. */
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

    /** Pose directement un état, sans passer par une écriture. */
    fun set(value: FeedFreshness) {
        freshness.value = value
    }
}
