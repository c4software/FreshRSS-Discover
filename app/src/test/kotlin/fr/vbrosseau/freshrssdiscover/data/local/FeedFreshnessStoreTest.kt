package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshness
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Une heure en millisecondes, pour lire les attentes sans compter. */
private const val ONE_HOUR_MILLIS = 60L * 60L * 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class FeedFreshnessStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private val clock = FakeClock(nowMillis = 1_700_000_000_000L)

    private lateinit var dataStore: DataStore<Preferences>

    private fun store(): FeedFreshnessStore {
        if (!::dataStore.isInitialized) {
            dataStore = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("fraicheur.preferences_pb").also(File::delete)
            }
        }
        return FeedFreshnessStore(dataStore, clock)
    }

    @After
    fun stopWriting() {
        scope.cancel()
    }

    @Test
    fun withoutAnyServerAnswerNothingIsKnown() = runTest {
        val freshness = store().observeFreshness().first()

        assertNull(freshness.lastRefreshEpochMillis)
        assertNull(freshness.acknowledgedRefreshEpochMillis)
    }

    @Test
    fun recordingARefreshWritesTheClockInstant() = runTest {
        val store = store()

        store.recordRefresh()

        assertEquals(clock.nowEpochMillis(), store.observeFreshness().first().lastRefreshEpochMillis)
    }

    @Test
    fun theSecondRefreshReplacesTheFirst() = runTest {
        val store = store()
        store.recordRefresh()

        val later = clock.advanceBy(ONE_HOUR_MILLIS)
        store.recordRefresh()

        assertEquals(later, store.observeFreshness().first().lastRefreshEpochMillis)
    }

    @Test
    fun acknowledgingCarriesTheTimestampInPlace() = runTest {
        val store = store()
        store.recordRefresh()

        store.acknowledgeStale()

        val freshness = store.observeFreshness().first()
        assertEquals(freshness.lastRefreshEpochMillis, freshness.acknowledgedRefreshEpochMillis)
    }

    @Test
    fun acknowledgingBeforeAnyRefreshSilencesNothing() = runTest {
        val store = store()

        store.acknowledgeStale()

        // Acquitter `null` puis obtenir une vraie date ne doit pas faire taire
        // l'avis : les deux valeurs cessent de correspondre.
        assertNull(store.observeFreshness().first().acknowledgedRefreshEpochMillis)
        store.recordRefresh()
        assertEquals(
            FeedFreshness(lastRefreshEpochMillis = clock.nowEpochMillis()),
            store.observeFreshness().first(),
        )
    }

    @Test
    fun aRefreshAfterAnAcknowledgementReopensTheNotice() = runTest {
        val store = store()
        store.recordRefresh()
        store.acknowledgeStale()

        val later = clock.advanceBy(ONE_HOUR_MILLIS)
        store.recordRefresh()

        val freshness = store.observeFreshness().first()
        assertEquals(later, freshness.lastRefreshEpochMillis)
        assertEquals(later - ONE_HOUR_MILLIS, freshness.acknowledgedRefreshEpochMillis)
        assertEquals(true, freshness.showsStaleNotice(later + 6 * ONE_HOUR_MILLIS))
    }

    @Test
    fun theFlowEmitsAgainAtEveryWrite() = runTest {
        val store = store()

        // Le collecteur est ouvert avant les écritures : l'écran doit voir la
        // date changer sans être reconstruit.
        val seen = mutableListOf<FeedFreshness>()
        val job = scope.launch { store.observeFreshness().toList(seen) }
        store.recordRefresh()
        job.cancel()

        assertEquals(2, seen.size)
        assertNull(seen.first().lastRefreshEpochMillis)
        assertEquals(clock.nowEpochMillis(), seen.last().lastRefreshEpochMillis)
    }

    @Test
    fun anAcknowledgementIsSharedAcrossObservers() = runTest {
        val store = store()
        store.recordRefresh()

        store.acknowledgeStale()

        // Deux observateurs, un seul dépôt : c'est ce qui fait qu'acquitter dans
        // un mode de présentation fait taire l'autre (SPECS.md §4.8).
        assertEquals(
            store.observeFreshness().first(),
            store.observeFreshness().first(),
        )
        assertEquals(
            clock.nowEpochMillis(),
            store.observeFreshness().first().acknowledgedRefreshEpochMillis,
        )
    }
}
