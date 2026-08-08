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

/** Minutes dans une heure, pour lire les attentes sans compter mentalement. */
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

    /** Place l'horloge à une date et une heure lisibles, dans [zone]. */
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
        // Le programmateur en déduit qu'il n'a rien à programmer : choisir une
        // heure par défaut serait exactement ce que SPECS.md §4.9 refuse.
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
        // Le cœur de SPECS.md §4.9 : l'heure recherchée est celle où
        // l'utilisateur tend la main vers l'application. Retenir la dernière
        // ouverture ferait tomber le rappel du lendemain à l'heure d'un coup
        // d'œil distrait avant de dormir.
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
        // Le jour se compte dans le fuseau de l'utilisateur et non en UTC : à
        // Paris, 0 h 30 est un jour nouveau, alors qu'en UTC il est encore
        // 23 h 30 la veille. Compté en UTC, ce second appel serait pris pour
        // une deuxième ouverture du même jour et n'écrirait rien.
        val store = store()
        clockAt(2026, 3, 4, hour = 9, minute = 0)
        store.recordOpening()

        clockAt(2026, 3, 5, hour = 0, minute = 30)
        store.recordOpening()

        assertEquals(minuteOf(0, 30), store.openingMinute())
    }

    @Test
    fun theMinuteIsCountedInTheZoneOfTheUserAndNotInUtc() = runTest {
        // Le même instant, lu depuis Tokyo : c'est l'heure qu'affiche le
        // téléphone de l'utilisateur qui doit être retenue, pas celle de
        // Greenwich.
        clockAt(2026, 3, 4, hour = 8, minute = 12, zone = TOKYO)

        val store = store(zone = TOKYO)
        store.recordOpening()

        assertEquals(minuteOf(8, 12), store.openingMinute())
    }

    @Test
    fun aStoredOpeningSurvivesAFreshStoreOnTheSameFile() = runTest {
        // C'est la raison d'être du stockage : le rappel doit partir un jour où
        // l'application n'a pas été rouverte, donc dans un autre processus.
        val store = store()
        clockAt(2026, 3, 4, hour = 8, minute = 12)
        store.recordOpening()

        assertEquals(minuteOf(8, 12), ReminderTimeStore(dataStore, clock, PARIS).openingMinute())
    }

    @Test
    fun anOutOfBoundsMinuteOnDiskIsReadAsNoOpeningAtAll() = runTest {
        // Écrite par une version antérieure, ou restaurée d'une sauvegarde :
        // la relayer à `DailyMinute` ferait lever son constructeur, et le
        // rappel resterait cassé jusqu'à une réinstallation.
        val store = store()
        dataStore.edit { it[intPreferencesKey("reminder.opening_minute")] = 5_000 }

        assertNull(store.openingMinute())
    }

    @Test
    fun theReminderKeysDoNotCollideWithTheOtherOnes() = runTest {
        // Quatre familles partagent le fichier — `session.`, `reading.`,
        // `display.` et `reminder.` : une collision ferait disparaître la
        // session à la première ouverture enregistrée.
        val store = store()
        clockAt(2026, 3, 4, hour = 8, minute = 12)
        store.recordOpening()

        val keys = dataStore.data.first().asMap().keys.map { it.name }.sorted()
        assertEquals(listOf("reminder.opening_day", "reminder.opening_minute"), keys)
    }
}
