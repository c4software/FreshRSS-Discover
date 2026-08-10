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

/** One hour in milliseconds, so expectations read without arithmetic. */
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

        // Acknowledging `null` then getting a real date must not silence the
        // notice: the two values stop matching.
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

        // The collector opens before the writes: the screen must see the
        // date change without being rebuilt.
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

        // Two observers, one store: acknowledging in one presentation mode
        // silences the other (SPECS.md §4.8).
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
