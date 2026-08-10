package fr.vbrosseau.freshrssdiscover.domain.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Minutes in an hour, named so the conversion reads clearly. */
private const val MINUTES_PER_HOUR = 60

/** Minutes in a day, exclusive upper bound of a time of day. */
const val MINUTES_PER_DAY: Int = 24 * MINUTES_PER_HOUR

/**
 * Time of day at which the reminder fires (SPECS.md §4.9).
 *
 * Expressed in minutes since midnight rather than `LocalTime`: it stores in a
 * `DataStore` without conversion, and seconds are meaningless for a reading
 * reminder.
 *
 * The retained time is the previous day's opening time. The reminder therefore
 * fires when the user habitually opens the app, not at a developer-chosen
 * hour: a 9 a.m. notification for someone who reads at night is an
 * interruption, not a reminder.
 */
@JvmInline
value class DailyMinute(val value: Int) {
    init {
        require(value in 0 until MINUTES_PER_DAY) { "moment de la journée hors bornes : $value" }
    }

    companion object {
        /** Time of day carried by this instant, in [zone]. */
        fun of(
            epochMillis: Long,
            zone: ZoneId,
        ): DailyMinute {
            val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
            return DailyMinute(time.hour * MINUTES_PER_HOUR + time.minute)
        }
    }
}

/**
 * When the next reminder must fire.
 *
 * Strictly in the future, which is the whole point of this function: at the
 * moment the user opens the app, today's opening time has by construction
 * already passed. Scheduling "today at that time" would fire the notification
 * immediately, i.e. while the user is reading.
 *
 * Strict inequality matters: at the exact second, the next day is targeted.
 *
 * @param zone the user's zone, passed in rather than read: the domain knows
 *   neither clock nor system settings, and a test must be able to run in Tokyo
 *   as well as Paris.
 */
fun nextReminderAt(
    at: DailyMinute,
    nowEpochMillis: Long,
    zone: ZoneId,
): Long {
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
    val target = LocalTime.of(at.value / MINUTES_PER_HOUR, at.value % MINUTES_PER_HOUR)

    val today = now.toLocalDate().atTime(target).atZone(zone)
    val chosen =
        if (today.toInstant().toEpochMilli() > nowEpochMillis) {
            today
        } else {
            now.toLocalDate().plusDays(1).atTime(target).atZone(zone)
        }

    return chosen.toInstant().toEpochMilli()
}
