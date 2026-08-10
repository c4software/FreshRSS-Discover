package fr.vbrosseau.freshrssdiscover.presentation.discover

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR

/** Average month, not calendar month: "2 months ago" does not need to be exact. */
private const val SECONDS_PER_MONTH = 30L * SECONDS_PER_DAY
private const val SECONDS_PER_YEAR = 365L * SECONDS_PER_DAY

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Age of an article, in an untranslated form.
 *
 * SPECS.md §4.3 requires a relative date ("2 h ago"); AGENTS.md §9 forbids
 * computing it in a Composable and requires every displayed string to be a
 * resource. Both are reconciled by separating the computation, testable on a
 * pure JVM, from its wording, done at display time.
 */
sealed interface RelativeTime {
    /** Under one minute: displaying "0 min ago" would be absurd. */
    data object JustNow : RelativeTime

    data class Minutes(val count: Int) : RelativeTime

    data class Hours(val count: Int) : RelativeTime

    data class Days(val count: Int) : RelativeTime

    data class Months(val count: Int) : RelativeTime

    data class Years(val count: Int) : RelativeTime
}

/**
 * Age of a publication at a given instant.
 *
 * Time comes from the domain `Clock`, never `System.currentTimeMillis()`
 * (AGENTS.md §2): otherwise the function would depend on the machine's clock
 * and would not be testable.
 *
 * A future date (server clock ahead, postdated article) is clamped to
 * [RelativeTime.JustNow] rather than shown as negative: it is off by a few
 * minutes, whereas "-3 min ago" would be wrong and unreadable.
 */
fun relativeTimeSince(
    publishedAtEpochSeconds: Long,
    nowEpochMillis: Long,
): RelativeTime {
    val elapsed = (nowEpochMillis / MILLIS_PER_SECOND - publishedAtEpochSeconds).coerceAtLeast(0L)

    return when {
        elapsed < SECONDS_PER_MINUTE -> RelativeTime.JustNow
        elapsed < SECONDS_PER_HOUR -> RelativeTime.Minutes((elapsed / SECONDS_PER_MINUTE).toInt())
        elapsed < SECONDS_PER_DAY -> RelativeTime.Hours((elapsed / SECONDS_PER_HOUR).toInt())
        elapsed < SECONDS_PER_MONTH -> RelativeTime.Days((elapsed / SECONDS_PER_DAY).toInt())
        elapsed < SECONDS_PER_YEAR -> RelativeTime.Months((elapsed / SECONDS_PER_MONTH).toInt())
        else -> RelativeTime.Years((elapsed / SECONDS_PER_YEAR).toInt())
    }
}
