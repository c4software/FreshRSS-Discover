package fr.vbrosseau.freshrssdiscover.domain.reminder

import kotlin.math.pow

/** Number of histogram bins: one per hour of the day. */
const val HISTOGRAM_BIN_COUNT: Int = 24

/**
 * Daily decay applied to every bin when a new day records its first session.
 *
 * 0.9 halves a bin's weight in about a week: a habit that moved — a new job,
 * a new commute — wins within days, while a single unusual evening barely
 * dents two weeks of routine. Without decay the histogram would freeze the
 * first month of usage forever.
 */
const val HISTOGRAM_DAILY_DECAY: Double = 0.9

/**
 * Total weight below which the histogram abstains (SPECS.md §4.9).
 *
 * Three fresh sessions, or a few older ones: below that, a single recording
 * would decide the reminder hour on its own — exactly the one-sample fragility
 * this histogram exists to replace. The caller falls back on the opening time.
 */
const val HISTOGRAM_SUFFICIENT_WEIGHT: Double = 3.0

/**
 * Days beyond which the decay is applied as if this many had passed.
 *
 * A device clock jumping years ahead would otherwise raise the decay factor
 * to a power that flushes the histogram to zero in one step. Thirty days of
 * decay already leaves about 4 % of a bin — indistinguishable from empty for
 * the dominant-hour question — so capping there loses nothing and keeps a
 * clock accident from erasing the habit record.
 */
private const val MAX_DECAY_DAYS = 30L

/**
 * When the user reads, hour by hour (SPECS.md §4.9).
 *
 * A 24-bin histogram of reading sessions rather than an average of their
 * times: for someone reading in the morning **and** in the evening, the
 * circular mean lands mid-afternoon — an hour they never read at. The densest
 * bin is by construction an hour the user actually reads.
 *
 * Immutable value: [record] returns a new histogram, which is what lets a
 * store persist each state without this class knowing anything about disks.
 * No clock is read — the caller passes the day and the hour — so a test can
 * replay any sequence of days.
 *
 * @property bins weight per hour of day, decayed by [HISTOGRAM_DAILY_DECAY]
 *   each day. A weight, not a count: yesterday's session weighs less than
 *   today's.
 * @property lastDay local day (epoch-based, see `localDayOf`) of the last
 *   recording, or `null` if nothing was ever recorded. What makes the lazy
 *   decay possible: the elapsed days are only known by comparison.
 * @property recordedHours hours already recorded for [lastDay]. At most one
 *   entry per day and per hour: one entry per **article** would let a
 *   forty-article catch-up evening outweigh two weeks of habit.
 */
data class ReadingHistogram(
    val bins: List<Double>,
    val lastDay: Long?,
    val recordedHours: Set<Int>,
) {
    init {
        require(bins.size == HISTOGRAM_BIN_COUNT) { "histogramme à ${bins.size} cases" }
        require(bins.all { it >= 0.0 && it.isFinite() }) { "poids invalide dans l'histogramme" }
        require(recordedHours.all { it in 0 until HISTOGRAM_BIN_COUNT }) { "heure hors bornes" }
    }

    /** Total remaining weight, the measure [isSufficient] rests on. */
    val totalWeight: Double get() = bins.sum()

    /**
     * Whether the histogram has seen enough to designate an hour.
     *
     * Decay makes this reversible: a user who stops reading for weeks sees
     * the histogram fall back below the threshold, and the reminder returns
     * to the opening-time behaviour instead of aiming at a stale habit.
     */
    val isSufficient: Boolean get() = totalWeight >= HISTOGRAM_SUFFICIENT_WEIGHT

    /**
     * The densest hour, or `null` while the histogram is insufficient.
     *
     * Ties break on the earliest hour — arbitrary, but deterministic: two
     * schedulings on the same data must aim at the same minute, for the same
     * reason `reminderPlanFor` refuses a random draw.
     */
    fun dominantHour(): Int? {
        if (!isSufficient) return null

        return bins.indices.maxBy(bins::get)
    }

    /**
     * Records a reading session at [hour] on [day].
     *
     * The decay of every bin is applied here, lazily, from the days elapsed
     * since [lastDay] — a histogram nobody feeds is never touched, and does
     * not need a daily job to age.
     *
     * A [day] **earlier** than [lastDay] — a device clock set back — records
     * without decaying and keeps [lastDay] where it was: moving it backwards
     * would make the next real day decay by the whole round trip, punishing
     * the histogram for a clock it does not own.
     */
    fun record(
        day: Long,
        hour: Int,
    ): ReadingHistogram {
        require(hour in 0 until HISTOGRAM_BIN_COUNT) { "heure hors bornes : $hour" }

        val last = lastDay
        return when {
            last == null || day > last -> {
                val elapsed = if (last == null) 0L else (day - last).coerceAtMost(MAX_DECAY_DAYS)
                val factor = HISTOGRAM_DAILY_DECAY.pow(elapsed.toDouble())

                ReadingHistogram(
                    bins = bins.mapIndexed { index, weight -> weight * factor + if (index == hour) 1.0 else 0.0 },
                    lastDay = day,
                    recordedHours = setOf(hour),
                )
            }

            day == last && hour in recordedHours -> this

            else ->
                ReadingHistogram(
                    bins = bins.mapIndexed { index, weight -> weight + if (index == hour) 1.0 else 0.0 },
                    lastDay = last,
                    recordedHours = if (day == last) recordedHours + hour else recordedHours,
                )
        }
    }

    companion object {
        /** The state before any reading was ever observed. */
        val Empty: ReadingHistogram =
            ReadingHistogram(
                bins = List(HISTOGRAM_BIN_COUNT) { 0.0 },
                lastDay = null,
                recordedHours = emptySet(),
            )
    }
}
