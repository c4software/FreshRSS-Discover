package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.reminder.HISTOGRAM_BIN_COUNT
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.reminder.ReadingSessionRecorder
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Persists the reading-hour histogram (SPECS.md §4.9).
 *
 * Shares the application's `DataStore<Preferences>` under the `reminder.`
 * prefix, like [ReminderTimeStore] and for the same reason: a logout only
 * wipes `session.` keys, and the reading habit belongs to the person, not to
 * their account.
 *
 * One string key rather than twenty-six scalar ones: the histogram is read
 * and written as a whole — bins, day and recorded hours move together — and
 * spreading it over one key per bin would let a partial write mix two states.
 *
 * The read-modify-write happens inside a single `edit`, which serializes
 * DataStore transactions: two batches of a scroll marking articles at the
 * same moment must not both read the same state and lose one session.
 *
 * Deliberately not `@Singleton`, like [ReminderTimeStore]: [zone] is read at
 * construction, and a cached instance would date sessions in the zone of the
 * country the user has left.
 */
internal class ReadingHistogramStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val zone: ZoneId,
) : ReadingSessionRecorder {

    override suspend fun recordSession() {
        val now = clock.nowEpochMillis()
        val day = localDayOf(now, zone)
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour

        dataStore.edit { preferences ->
            val recorded = parse(preferences[Keys.Histogram]).record(day, hour)
            preferences[Keys.Histogram] = serialize(recorded)
        }
    }

    override suspend fun histogram(): ReadingHistogram = parse(dataStore.data.first()[Keys.Histogram])

    private object Keys {
        val Histogram = stringPreferencesKey("reminder.reading_histogram")
    }
}

/** Bits of the recorded-hours mask all set: the highest value [serialize] can produce. */
private const val FULL_HOURS_MASK = (1 shl HISTOGRAM_BIN_COUNT) - 1

/** Day, recorded-hours mask, bins: what [serialize] writes, in that order. */
private const val SERIALIZED_PART_COUNT = 3

/**
 * `day;recordedHoursMask;bin,bin,…` — writable by [serialize] alone, hence
 * always with a known day: an empty histogram is simply never written.
 */
private fun serialize(histogram: ReadingHistogram): String {
    val mask = histogram.recordedHours.fold(0) { acc, hour -> acc or (1 shl hour) }
    return "${histogram.lastDay};$mask;${histogram.bins.joinToString(",")}"
}

/**
 * A value that does not parse is an empty histogram, never an exception: the
 * file may come from a backup or a future version, and failing here would
 * break both the reminder scheduling and the next recording — the write that
 * would have repaired it.
 */
private fun parse(raw: String?): ReadingHistogram {
    if (raw == null) return ReadingHistogram.Empty

    // `ReadingHistogram`'s constructor already refuses a wrong bin count, a
    // negative weight or a NaN: the validation is not repeated here, it is
    // caught. `runCatching` also covers the number parsing, whose failures are
    // exactly the same case — a file this version did not write.
    return runCatching {
        val (day, mask, bins) = raw.split(';').also { require(it.size == SERIALIZED_PART_COUNT) }
        val maskValue = mask.toInt().also { require(it in 0..FULL_HOURS_MASK) }

        ReadingHistogram(
            bins = bins.split(',').map(String::toDouble),
            lastDay = day.toLong(),
            recordedHours = (0 until HISTOGRAM_BIN_COUNT).filter { maskValue and (1 shl it) != 0 }.toSet(),
        )
    }.getOrDefault(ReadingHistogram.Empty)
}
