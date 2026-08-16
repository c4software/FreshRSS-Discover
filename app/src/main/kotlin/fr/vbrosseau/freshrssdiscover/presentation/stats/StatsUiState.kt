package fr.vbrosseau.freshrssdiscover.presentation.stats

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram

/**
 * One bar of the reading histogram, ready to draw.
 *
 * [fraction] is the bar's height relative to the tallest bar, not to the
 * total: the chart answers "when do I read", a comparison between hours, and
 * normalizing to the maximum uses the full drawing height whatever the
 * absolute weights are.
 */
data class StatsBar(
    val hour: Int,
    val fraction: Float,
)

/**
 * State displayed by the statistics screen (SPECS.md §6).
 *
 * [dominantHour] is exactly what the reminder targets (SPECS.md §4.9): the
 * screen exists to show the reminder's reasoning, so it must publish the same
 * decision, not a recomputation of its own.
 */
data class StatsUiState(
    val bars: List<StatsBar> = emptyList(),
    val dominantHour: Int? = null,
    val hasData: Boolean = false,
)

/**
 * Derives the display state from the domain histogram.
 *
 * A pure function outside the ViewModel, like the settings converters: the
 * normalization is a computation, and it is verified without a coroutine or
 * an injection graph.
 */
fun statsUiStateOf(histogram: ReadingHistogram): StatsUiState {
    val heaviest = histogram.bins.max()

    return StatsUiState(
        bars = histogram.bins.mapIndexed { hour, weight ->
            StatsBar(
                hour = hour,
                fraction = if (heaviest > 0.0) (weight / heaviest).toFloat() else 0f,
            )
        },
        dominantHour = histogram.dominantHour(),
        hasData = histogram.totalWeight > 0.0,
    )
}
