package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private lateinit var dataStore: DataStore<Preferences>

    private fun store(): SettingsStore {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("reglages.preferences_pb").also(java.io.File::delete)
        }
        return SettingsStore(dataStore)
    }

    @After
    fun stopWriting() {
        scope.cancel()
    }

    @Test
    fun withoutAnythingStoredTheSpecifiedDefaultsApply() = runTest {
        // What made duplication dangerous: what the screen shows on first
        // opening must be what the detector actually applies (SPECS.md §4.5).
        assertEquals(ReadingSettings.Default, store().observeReadingSettings().first())
    }

    @Test
    fun aStoredVisibleFractionIsReadBack() = runTest {
        val store = store()

        store.setVisibleFraction(0.8f)

        assertEquals(0.8f, store.observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun aStoredContinuousVisibilityIsReadBack() = runTest {
        val store = store()

        store.setContinuousVisibilityMillis(3_000L)

        assertEquals(3_000L, store.observeReadingSettings().first().continuousVisibilityMillis)
    }

    @Test
    fun changingOneThresholdLeavesTheOtherAlone() = runTest {
        val store = store()
        store.setVisibleFraction(1.0f)

        store.setContinuousVisibilityMillis(2_000L)

        val settings = store.observeReadingSettings().first()
        assertEquals(1.0f, settings.visibleFraction)
        assertEquals(2_000L, settings.continuousVisibilityMillis)
    }

    @Test
    fun aStoredValueSurvivesAFreshStoreOnTheSameFile() = runTest {
        // A setting that does not survive closing is worse than no setting.
        // A second `SettingsStore` built on the same `DataStore` replays the
        // startup read.
        val store = store()
        store.setVisibleFraction(0.4f)

        assertEquals(0.4f, SettingsStore(dataStore).observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun aCorruptedVisibleFractionOnDiskIsBroughtBackWithinBounds() = runTest {
        // Written by an earlier version, or restored from a backup: refusing
        // to start over a secondary setting would be worse than correcting
        // the value.
        val store = store()
        dataStore.edit { it[floatPreferencesKey("reading.visible_fraction")] = 5f }

        assertEquals(1.0f, store.observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun aCorruptedContinuousVisibilityOnDiskIsBroughtBackWithinBounds() = runTest {
        val store = store()
        dataStore.edit { it[longPreferencesKey("reading.continuous_visibility_millis")] = -1L }

        assertEquals(1_000L, store.observeReadingSettings().first().continuousVisibilityMillis)
    }

    @Test
    fun aNotANumberVisibleFractionOnDiskFallsBackToTheDefault() = runTest {
        // `NaN` compares to nothing: untreated, it would pass through the
        // clamping and make the threshold unreachable.
        val store = store()
        dataStore.edit { it[floatPreferencesKey("reading.visible_fraction")] = Float.NaN }

        assertEquals(0.6f, store.observeReadingSettings().first().visibleFraction)
    }

    @Test
    fun writingAVisibleFractionOutOfBoundsIsRefused() = runTest {
        // No UI control can produce this value: it signals a programming
        // fault, and storing it would freeze the fault in place.
        assertFailsWith<IllegalArgumentException> { store().setVisibleFraction(1.5f) }
    }

    @Test
    fun writingAContinuousVisibilityOutOfBoundsIsRefused() = runTest {
        assertFailsWith<IllegalArgumentException> { store().setContinuousVisibilityMillis(0L) }
    }

    @Test
    fun theReadingKeysDoNotCollideWithTheSessionOnes() = runTest {
        // Both stores share the same file: a key collision would wipe the
        // session on the first modified setting.
        val store = store()
        store.setVisibleFraction(0.4f)

        val keys = dataStore.data.first().asMap().keys.map { it.name }
        assertEquals(listOf("reading.visible_fraction"), keys)
    }

    @Test
    fun withoutAnythingStoredTheAutomaticMarkingIsOn() = runTest {
        // An installation predating the setting: the key is missing, and the
        // marking must remain what SPECS.md §1 describes.
        assertTrue(store().observeReadingSettings().first().autoMarkAsReadEnabled)
    }

    @Test
    fun theAutomaticMarkingCanBeTurnedOffAndReadBack() = runTest {
        val store = store()

        store.setAutoMarkAsReadEnabled(false)

        assertFalse(store.observeReadingSettings().first().autoMarkAsReadEnabled)
    }

    @Test
    fun theAutomaticMarkingSwitchSurvivesAFreshStoreOnTheSameFile() = runTest {
        // A setting that must be turned off again at every launch is not one.
        val store = store()
        store.setAutoMarkAsReadEnabled(false)

        assertFalse(SettingsStore(dataStore).observeReadingSettings().first().autoMarkAsReadEnabled)
    }

    @Test
    fun turningTheAutomaticMarkingOffLeavesTheThresholdsStored() = runTest {
        // The thresholds are grayed out, not forgotten: they must reappear
        // unchanged when re-enabled.
        val store = store()
        store.setVisibleFraction(0.8f)
        store.setContinuousVisibilityMillis(3_000L)

        store.setAutoMarkAsReadEnabled(false)

        val settings = store.observeReadingSettings().first()
        assertEquals(0.8f, settings.visibleFraction)
        assertEquals(3_000L, settings.continuousVisibilityMillis)
    }

    @Test
    fun theAutomaticMarkingKeyDoesNotCollideWithTheOtherOnes() = runTest {
        val store = store()
        store.setAutoMarkAsReadEnabled(false)

        val keys = dataStore.data.first().asMap().keys.map { it.name }
        assertEquals(listOf("reading.auto_mark_as_read"), keys)
    }

    @Test
    fun switchingTheAutomaticMarkingComesThroughAsANewEmission() = runTest {
        // `distinctUntilChanged` covers the whole settings object: the third
        // field must participate, otherwise turning it off would never reach
        // the feed ViewModels.
        val store = store()
        val seen = mutableListOf<ReadingSettings>()
        val job = scope.launch { store.observeReadingSettings().toList(seen) }

        store.setAutoMarkAsReadEnabled(false)
        job.cancel()

        assertEquals(2, seen.size)
        assertFalse(seen.last().autoMarkAsReadEnabled)
    }

    @Test
    fun withoutAnythingStoredTheFeedIsPresentedAsAList() = runTest {
        // SPECS.md §4.8: List is the default mode.
        assertEquals(FeedPresentation.List, store().observeFeedPresentation().first())
    }

    @Test
    fun aStoredFeedPresentationIsReadBack() = runTest {
        val store = store()

        store.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(FeedPresentation.Swipe, store.observeFeedPresentation().first())
    }

    @Test
    fun theFeedPresentationSurvivesAFreshStoreOnTheSameFile() = runTest {
        // SPECS.md §4.8: the app reopens in the mode the user left. A second
        // store on the same file replays that startup.
        val store = store()
        store.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(FeedPresentation.Swipe, SettingsStore(dataStore).observeFeedPresentation().first())
    }

    @Test
    fun aCorruptedFeedPresentationOnDiskFallsBackToTheList() = runTest {
        // An unreadable mode must not block startup: the feed opens in List,
        // and the next choice rewrites the value.
        val store = store()
        dataStore.edit { it[stringPreferencesKey("display.feed_presentation")] = "Carrousel" }

        assertEquals(FeedPresentation.List, store.observeFeedPresentation().first())
    }

    @Test
    fun changingTheFeedPresentationLeavesTheReadingThresholdsAlone() = runTest {
        val store = store()
        store.setVisibleFraction(0.8f)

        store.setFeedPresentation(FeedPresentation.Swipe)

        assertEquals(0.8f, store.observeReadingSettings().first().visibleFraction)
        assertEquals(FeedPresentation.Swipe, store.observeFeedPresentation().first())
    }

    @Test
    fun theFeedPresentationKeyDoesNotCollideWithTheOtherOnes() = runTest {
        // Three key families share the file (`session.`, `reading.` and
        // `display.`): a collision would wipe one on the first change of
        // another.
        val store = store()
        store.setFeedPresentation(FeedPresentation.Swipe)

        val keys = dataStore.data.first().asMap().keys.map { it.name }
        assertEquals(listOf("display.feed_presentation"), keys)
    }

    @Test
    fun writingAnUnrelatedPreferenceDoesNotReEmitTheSettings() = runTest {
        // DataStore emits on every write to the file, not the key. Without
        // `distinctUntilChanged`, the last-server-contact date (written on
        // every received page) would re-emit unchanged settings, and the
        // feed ViewModels would rebuild their read detector, resetting the
        // in-flight visibility timers (SPECS.md §4.5) mid-read.
        val store = store()
        val seen = mutableListOf<ReadingSettings>()
        val job = scope.launch { store.observeReadingSettings().toList(seen) }

        dataStore.edit { it[longPreferencesKey("feed.last_refresh_at")] = 1L }
        dataStore.edit { it[longPreferencesKey("feed.last_refresh_at")] = 2L }
        job.cancel()

        assertEquals(1, seen.size)
    }

    @Test
    fun aRealSettingChangeStillComesThrough() = runTest {
        val store = store()
        val seen = mutableListOf<ReadingSettings>()
        val job = scope.launch { store.observeReadingSettings().toList(seen) }

        store.setVisibleFraction(0.8f)
        job.cancel()

        assertEquals(2, seen.size)
        assertEquals(0.8f, seen.last().visibleFraction)
    }

    @Test
    fun anUnrelatedWriteDoesNotReEmitThePresentationMode() = runTest {
        val store = store()
        val seen = mutableListOf<FeedPresentation>()
        val job = scope.launch { store.observeFeedPresentation().toList(seen) }

        dataStore.edit { it[longPreferencesKey("feed.last_refresh_at")] = 1L }
        job.cancel()

        assertEquals(1, seen.size)
    }
}
