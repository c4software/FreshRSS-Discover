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
            histogram.dominantHour()?.let { DailyMinute(it * MINUTES_PER_HOUR) } ?: openingMinute
    }
