package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.reminder.DailyMinute
import fr.vbrosseau.freshrssdiscover.domain.reminder.MINUTES_PER_DAY
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.reminder.OpeningRecorder
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Local day number carried by this instant, counted from the epoch.
 *
 * Two needs share this function, which is why it is named here rather than
 * duplicated: knowing whether the day changed since the last opening, and
 * giving the domain the index that rotates the reminder wording
 * (`reminderPlanFor`). Both must count the same days, otherwise the wording
 * would change in the middle of the user's day.
 *
 * Local, never UTC: "the first opening of the day" is counted in the day of
 * the person opening the application, not Greenwich's. In Paris, an opening at
 * 1 a.m. already belongs to the next day; in UTC it would still belong to the
 * previous one.
 */
internal fun localDayOf(
    epochMillis: Long,
    zone: ZoneId,
): Long = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

/**
 * Records the time of the first opening of the day (SPECS.md §4.9).
 *
 * That time is what the next day's reminder targets: the moment the user
 * reaches for the application. Recording the last opening would instead
 * capture a distracted visit — a thirty-second glance before sleep would pin
 * the reminder to midnight.
 *
 * Hence the two keys. The time alone would not suffice: without knowing which
 * day it refers to, the first opening of the day could not be distinguished
 * from later ones, and every return to the application would overwrite the
 * recorded time. The stored day is what makes [recordOpening] a no-op from the
 * second call onward.
 *
 * Shares the application's `DataStore<Preferences>`, with the `reminder.`
 * prefix — the same as the setting held by [SettingsStore]. A logout only
 * wipes `session.` keys and therefore leaves the time in place: the user's
 * reading habit does not belong to their account.
 *
 * Deliberately not `@Singleton`. The object holds no state — the state lives
 * in the `DataStore`, which is the singleton — and its [zone] is read at
 * construction: a single instance would freeze the startup time zone for the
 * life of the process, and a user who changes country would keep seeing their
 * openings dated in the old one.
 */
internal class ReminderTimeStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val zone: ZoneId,
) : OpeningRecorder {

    /**
     * The day check and the write are in the same `edit`: DataStore serializes
     * transactions there, which a `first()` followed by an `edit` would not
     * guarantee. Two simultaneous openings — an `Activity` recreated during a
     * rotation is one — could otherwise both pass the check, and the second
     * would overwrite the first.
     */
    override suspend fun recordOpening() {
        val now = clock.nowEpochMillis()
        val day = localDayOf(now, zone)

        dataStore.edit { preferences ->
            if (preferences[Keys.OpeningDay] == day) return@edit

            preferences[Keys.OpeningDay] = day
            preferences[Keys.OpeningMinute] = DailyMinute.of(now, zone).value
        }
    }

    /**
     * One-shot read rather than a `Flow`: the caller is the scheduler, which
     * decides once and has nothing to re-observe.
     *
     * An out-of-range value is treated as absent rather than passed to
     * `DailyMinute`, whose constructor would throw. The file may have been
     * written by an earlier version or restored from a backup, and failing the
     * reminder scheduling for that would also prevent the next opening from
     * writing a valid value.
     */
    override suspend fun openingMinute(): DailyMinute? =
        dataStore.data.first()[Keys.OpeningMinute]
            ?.takeIf { it in 0 until MINUTES_PER_DAY }
            ?.let(::DailyMinute)

    private object Keys {
        val OpeningMinute = intPreferencesKey("reminder.opening_minute")

        /**
         * The day [OpeningMinute] refers to. Without it, the recorded time
         * would be the last opening's rather than the first's.
         */
        val OpeningDay = longPreferencesKey("reminder.opening_day")
    }
}
