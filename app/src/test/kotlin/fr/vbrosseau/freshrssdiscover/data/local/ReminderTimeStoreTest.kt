package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.reminder.DailyMinute
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

/** Minutes per hour, so expectations read without mental arithmetic. */
private const val MINUTES_PER_HOUR = 60

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderTimeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private val clock = FakeClock()

    private lateinit var dataStore: DataStore<Preferences>

    private fun store(zone: ZoneId = PARIS): ReminderTimeStore {
        if (!::dataStore.isInitialized) {
            dataStore = PreferenceDataStoreFactory.create(scope = scope) {
                folder.newFile("rappel.preferences_pb").also(File::delete)
            }
        }
        return ReminderTimeStore(dataStore, clock, zone)
    }

    @After
    fun stopWriting() {
        scope.cancel()
    }

    /** Sets the clock to a readable date and time, in [zone]. */
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

    private fun minuteOf(
        hour: Int,
        minute: Int,
    ) = DailyMinute(hour * MINUTES_PER_HOUR + minute)

    @Test
    fun withoutAnyOpeningNoMinuteIsKnown() = runTest {
        // The scheduler concludes there is nothing to schedule: a default
        // time would be exactly what SPECS.md §4.9 refuses.
        assertNull(store().openingMinute())
    }

    @Test
    fun theFirstOpeningOfTheDayIsRemembered() = runTest {
        val store = store()
        clockAt(2026, 3, 4, hour = 8, minute = 12)

        store.recordOpening()

        assertEquals(minuteOf(8, 12), store.openingMinute())
    }

    @Test
    fun aSecondOpeningTheSameDayIsIgnored() = runTest {
        // The core of SPECS.md §4.9: the time sought is when the user
        // reaches for the app. Keeping the last opening would place the next
        // day's reminder at the time of a distracted glance before sleep.
        val store = store()
        clockAt(2026, 3, 4, hour = 8, minute = 12)
        store.recordOpening()

        clockAt(2026, 3, 4, hour = 23, minute = 40)
        store.recordOpening()

        assertEquals(minuteOf(8, 12), store.openingMinute())
    }

    @Test
    fun theFirstOpeningOfANewDayReplacesThePreviousOne() = runTest {
        val store = store()
        clockAt(2026, 3, 4, hour = 8, minute = 12)
        store.recordOpening()

        clockAt(2026, 3, 5, hour = 21, minute = 5)
        store.recordOpening()

        assertEquals(minuteOf(21, 5), store.openingMinute())
    }

    @Test
    fun anOpeningJustAfterMidnightBelongsToTheNewDay() = runTest {
        // The day is counted in the user's zone, not UTC: in Paris, 00:30 is
        // a new day while in UTC it is still 23:30 the previous day. Counted
        // in UTC, this second call would look like a second opening of the
        // same day and write nothing.
        val store = store()
        clockAt(2026, 3, 4, hour = 9, minute = 0)
        store.recordOpening()

        clockAt(2026, 3, 5, hour = 0, minute = 30)
        store.recordOpening()

        assertEquals(minuteOf(0, 30), store.openingMinute())
    }

    @Test
    fun theMinuteIsCountedInTheZoneOfTheUserAndNotInUtc() = runTest {
        // The same instant, read from Tokyo: the time shown on the user's
        // phone must be kept, not Greenwich time.
        clockAt(2026, 3, 4, hour = 8, minute = 12, zone = TOKYO)

        val store = store(zone = TOKYO)
        store.recordOpening()

        assertEquals(minuteOf(8, 12), store.openingMinute())
    }

    @Test
    fun aStoredOpeningSurvivesAFreshStoreOnTheSameFile() = runTest {
        // The storage's reason for being: the reminder must fire on a day
        // the app was not reopened, hence in another process.
        val store = store()
        clockAt(2026, 3, 4, hour = 8, minute = 12)
        store.recordOpening()

        assertEquals(minuteOf(8, 12), ReminderTimeStore(dataStore, clock, PARIS).openingMinute())
    }

    @Test
    fun anOutOfBoundsMinuteOnDiskIsReadAsNoOpeningAtAll() = runTest {
        // Written by an earlier version, or restored from a backup: passing
        // it to `DailyMinute` would make its constructor throw, leaving the
        // reminder broken until a reinstall.
        val store = store()
        dataStore.edit { it[intPreferencesKey("reminder.opening_minute")] = 5_000 }

        assertNull(store.openingMinute())
    }

    @Test
    fun theReminderKeysDoNotCollideWithTheOtherOnes() = runTest {
        // Four key families share the file (`session.`, `reading.`,
        // `display.` and `reminder.`): a collision would wipe the session on
        // the first recorded opening.
        val store = store()
        clockAt(2026, 3, 4, hour = 8, minute = 12)
        store.recordOpening()

        val keys = dataStore.data.first().asMap().keys.map { it.name }.sorted()
        assertEquals(listOf("reminder.opening_day", "reminder.opening_minute"), keys)
    }
}
