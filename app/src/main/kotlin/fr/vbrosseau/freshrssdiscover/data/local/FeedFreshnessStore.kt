package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshness
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feed freshness: the timestamp is persisted, the acknowledgement kept in memory.
 *
 * Two storage media, deliberately. The last server contact date is a scalar to
 * keep across launches — hence DataStore, per ARCHITECTURE.md §5.1. The
 * acknowledgement has no reason to survive the process: on reopening, either a
 * request succeeds and the date is updated, or it fails and the offline banner
 * takes over. Persisting it would add a key for a situation that never occurs.
 *
 * `@Singleton` is a necessity here, not a habit: the in-memory acknowledgement
 * must be shared. The two presentation modes (SPECS.md §4.8) each have their
 * own ViewModel, and switching between them destroys one to build the other.
 * An acknowledgement held by the ViewModel would make the banner reappear on
 * every switch.
 */
@Singleton
internal class FeedFreshnessStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : FeedFreshnessRepository {
    private val acknowledged = MutableStateFlow<Long?>(null)

    override fun observeFreshness(): Flow<FeedFreshness> =
        combine(
            dataStore.data.map { it[LastRefreshAtKey] },
            acknowledged,
        ) { lastRefreshAt, acknowledgedAt ->
            FeedFreshness(
                lastRefreshEpochMillis = lastRefreshAt,
                acknowledgedRefreshEpochMillis = acknowledgedAt,
            )
        }

    override suspend fun recordRefresh() {
        val now = clock.nowEpochMillis()
        dataStore.edit { preferences -> preferences[LastRefreshAtKey] = now }
    }

    /**
     * Acknowledges the timestamp as written, not the current instant.
     *
     * This is what makes the acknowledgement expire on its own: the next
     * server contact changes the date, the acknowledgement no longer matches
     * it, and the notice can reappear six hours later (see [FeedFreshness]).
     */
    override suspend fun acknowledgeStale() {
        acknowledged.value = dataStore.data.first()[LastRefreshAtKey]
    }

    private companion object {
        /**
         * Prefix `feed.`, distinct from `session.`, `reading.` and the
         * settings: all four share the same file, and a targeted wipe must
         * only remove what it aims at.
         */
        val LastRefreshAtKey = longPreferencesKey("feed.last_refresh_at")
    }
}
