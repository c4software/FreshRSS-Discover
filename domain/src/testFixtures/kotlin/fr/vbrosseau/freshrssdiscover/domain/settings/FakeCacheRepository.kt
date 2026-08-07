package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cache en mémoire, pour les tests.
 *
 * Il **respecte l'invariant du vrai dépôt** : la purge n'emporte que les
 * articles purgeables et laisse les autres. Un fake qui viderait tout laisserait
 * passer un ViewModel qui promet à l'écran une suppression complète.
 */
class FakeCacheRepository(
    initial: CacheStatus = CacheStatus.Empty,
) : CacheRepository {
    private val status = MutableStateFlow(initial)

    /** Nombre de purges demandées, pour vérifier qu'un geste en déclenche exactement une. */
    var purgeCount: Int = 0
        private set

    /** État courant, pour vérifier l'effet d'une purge sans collecter. */
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
