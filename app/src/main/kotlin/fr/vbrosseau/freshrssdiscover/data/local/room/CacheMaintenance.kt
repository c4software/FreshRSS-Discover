package fr.vbrosseau.freshrssdiscover.data.local.room

import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local cache maintenance: the purge, and measuring what remains
 * (SPECS.md §5.4, §6).
 *
 * The automatic purge runs once per process start, in the background. The
 * three possible moments do not cost the same:
 *
 * - After each page: the most frequent, and the worst moment. A page arrives
 *   while the user scrolls; a `DELETE` scanning the whole table and querying
 *   the mark queue would land exactly when a stutter is visible. Twenty to
 *   thirty times per session for a result that only changes over days.
 * - Periodically: would require a scheduler (`WorkManager`), hence one more
 *   dependency and component, for an operation with no reason to run while
 *   the application is closed — a cache nobody reads bothers nobody.
 * - At startup: once per process, on [ApplicationScope], with nothing
 *   awaiting it. The first display comes from the cache (SPECS.md §5.1) and
 *   is not suspended on this coroutine, so the purge cannot delay what the
 *   user is looking at. This is the chosen option.
 *
 * The only perceptible effect is that articles read more than a week ago
 * disappear from the bottom of the feed between launches — that is the purge
 * itself, not its timing.
 */
@Singleton
internal class CacheMaintenance @Inject constructor(
    private val cache: ArticleCache,
    @ApplicationScope private val scope: CoroutineScope,
) : CacheRepository {

    override fun observeCacheStatus(): Flow<CacheStatus> = cache.observeCacheStatus()

    /**
     * Manual purge: everything read and synchronized, without waiting the
     * seven days.
     *
     * Dropping the age condition weakens no guarantee: unread articles and
     * pending marks remain out of reach.
     */
    override suspend fun purgeReadArticles(): Int = cache.purgeAllRead()

    /**
     * Starts the age-based purge without awaiting it.
     *
     * Call once at startup. It returns nothing: no decision depends on its
     * result, and awaiting it would only add delay. Failure is absorbed by
     * the application scope's `SupervisorJob` — an unpurged cache is an
     * inconvenience, not an outage.
     */
    fun purgeExpiredInBackground() {
        scope.launch {
            val removed = cache.purgeReadOlderThan(CacheRepository.MaxAge)
            Timber.i("Purge du cache : %d article(s) lu(s) et synchronisé(s) supprimé(s)", removed)
        }
    }
}
