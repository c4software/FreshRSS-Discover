package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

private val HistogramKey = stringPreferencesKey("reminder.reading_histogram")

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingHistogramStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private val clock = FakeClock()

    private lateinit var dataStore: DataStore<Preferences>

    private fun store(zone: ZoneId = PARIS): ReadingHistogramStore {
        if (!::dataStore.isInitialized) {
            dataStore = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("histogramme.preferences_pb").also(File::delete)
            }
        }
        return ReadingHistogramStore(dataStore, clock, zone)
    }

    @After
    fun stopWriting() {
        scope.cancel()
    }

    private fun clockAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId = PARIS,
    ) {
        clock.setTo(
            ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), zone)
                .toInstant()
                .toEpochMilli(),
        )
    }

    @Test
    fun withoutAnySessionTheHistogramIsEmpty() = runTest {
        assertEquals(ReadingHistogram.Empty, store().histogram())
    }

    @Test
    fun aSessionLandsInTheBinOfItsLocalHour() = runTest {
        val store = store()
        clockAt(2026, 3, 4, hour = 21, minute = 12)

        store.recordSession()

        assertEquals(1.0, store.histogram().bins[21])
    }

    @Test
    fun repeatedSessionsInTheSameHourCountOnce() = runTest {
        // The store relays the domain's rule; this checks the relaying, not
        // the rule: without the read-modify-write in one `edit`, the second
        // call could overwrite the first instead of deferring to it.
        val store = store()
        clockAt(2026, 3, 4, hour = 21, minute = 12)
        store.recordSession()
        clockAt(2026, 3, 4, hour = 21, minute = 40)
        store.recordSession()

        assertEquals(1.0, store.histogram().bins[21])
    }

    @Test
    fun theHourIsCountedInTheZoneOfTheUserAndNotInUtc() = runTest {
        clockAt(2026, 3, 4, hour = 21, minute = 0, zone = TOKYO)

        val store = store(zone = TOKYO)
        store.recordSession()

        // In UTC this instant is noon: bin 21 is only right in Tokyo.
        assertEquals(1.0, store.histogram().bins[21])
    }

    @Test
    fun aStoredHistogramSurvivesAFreshStoreOnTheSameFile() = runTest {
        val store = store()
        clockAt(2026, 3, 4, hour = 21, minute = 12)
        store.recordSession()

        assertEquals(store.histogram(), ReadingHistogramStore(dataStore, clock, PARIS).histogram())
    }

    @Test
    fun anUnreadableValueOnDiskIsReadAsAnEmptyHistogram() = runTest {
        // A backup, or a future version's format: failing would break both
        // the scheduling and the very write that would repair the value.
        val store = store()
        dataStore.edit { it[HistogramKey] = "n'importe quoi;;" }

        assertEquals(ReadingHistogram.Empty, store.histogram())
    }

    @Test
    fun anUnreadableValueIsRepairedByTheNextSession() = runTest {
        val store = store()
        dataStore.edit { it[HistogramKey] = "998;pas-un-masque;1,2,3" }

        clockAt(2026, 3, 4, hour = 21, minute = 12)
        store.recordSession()

        assertEquals(1.0, store.histogram().bins[21])
    }

    @Test
    fun sessionsAccumulateAcrossDaysWithDecay() = runTest {
        val store = store()
        clockAt(2026, 3, 4, hour = 21, minute = 12)
        store.recordSession()
        clockAt(2026, 3, 5, hour = 21, minute = 3)
        store.recordSession()

        val expected =
            ReadingHistogram.Empty
                .record(day = LocalDate.of(2026, 3, 4).toEpochDay(), hour = 21)
                .record(day = LocalDate.of(2026, 3, 5).toEpochDay(), hour = 21)
        assertEquals(expected, store.histogram())
    }
}
