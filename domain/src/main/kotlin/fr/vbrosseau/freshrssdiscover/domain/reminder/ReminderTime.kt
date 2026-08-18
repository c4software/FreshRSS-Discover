package fr.vbrosseau.freshrssdiscover.domain.reminder

/**
 * How the reminder hour is chosen (SPECS.md §4.9, §6).
 *
 * [Automatic] is the default: the application learns. [Fixed] exists because
 * the learned hour can be wrong for reasons no histogram sees — a night
 * shift, a shared device — and the person it bothers must be able to settle
 * it themselves. A **user**-chosen hour is not the developer-chosen hour
 * §4.9 refuses.
 */
sealed interface ReminderTime {
    /** The dominant reading hour, falling back on the opening time. */
    data object Automatic : ReminderTime

    /** An hour the user chose, applied as is. */
    data class Fixed(val at: DailyMinute) : ReminderTime
}

/** First minute of the nightly silence: no learned reminder from 22:00 (SPECS.md §4.9). */
const val QUIET_NIGHT_START_MINUTE: Int = 22 * MINUTES_PER_HOUR

/** First minute after the nightly silence: learned reminders resume at 07:00. */
const val QUIET_NIGHT_END_MINUTE: Int = 7 * MINUTES_PER_HOUR

/**
 * The minute of day the next reminder should aim at, or `null` for none.
 *
 * The resolution order is the whole rule: a fixed hour wins uncontested;
 * otherwise the histogram decides if it has seen enough ([ReadingHistogram]
 * abstains below its threshold); otherwise the previous day's opening minute
 * — the behaviour the application had before the histogram existed, kept as
 * the cold-start floor rather than a developer-chosen default.
 *
 * The dominant hour is aimed at its **start**: the histogram says "the user
 * reads during this hour", and a reminder at the start of the slot arrives
 * before the habit, not after it.
 *
 * A learned minute never lands in the night: both automatic sources go
 * through [awayFromQuietNight]. The fixed hour does not — it is applied as
 * is, as promised above.
 *
 * `null` means nothing is known at all — first launch, before any opening —
 * and the caller schedules nothing.
 */
fun reminderTargetMinute(
    time: ReminderTime,
    histogram: ReadingHistogram,
    openingMinute: DailyMinute?,
): DailyMinute? =
    when (time) {
        is ReminderTime.Fixed -> time.at
        ReminderTime.Automatic ->
            (histogram.dominantHour()?.let { DailyMinute(it * MINUTES_PER_HOUR) } ?: openingMinute)
                ?.awayFromQuietNight()
    }

/**
 * The same minute, pushed out of the night (SPECS.md §4.9).
 *
 * No learned reminder fires between 22:00 and 07:00 (author's ruling,
 * 2026-08-18). A learned minute can legitimately land there — an evening
 * session spilling past midnight credits the small hours, which is how a
 * real histogram came to aim at 00:00 — but a notification nobody is awake
 * for is not a reminder. The minute moves to the **nearest edge** of the
 * allowed day, 21:59 or 07:00: a midnight target belongs to the evening
 * reader who produced it, a 5 a.m. one to the morning. Distances are
 * circular, and never equal — the window's midpoint falls between two
 * minutes — so the choice is deterministic without a tie rule.
 */
fun DailyMinute.awayFromQuietNight(): DailyMinute {
    if (value in QUIET_NIGHT_END_MINUTE until QUIET_NIGHT_START_MINUTE) return this

    val eveningEdge = QUIET_NIGHT_START_MINUTE - 1
    val toEvening = (value - eveningEdge).mod(MINUTES_PER_DAY)
    val toMorning = (QUIET_NIGHT_END_MINUTE - value).mod(MINUTES_PER_DAY)
    return if (toEvening < toMorning) DailyMinute(eveningEdge) else DailyMinute(QUIET_NIGHT_END_MINUTE)
}
