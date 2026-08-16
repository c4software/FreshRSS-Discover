package fr.vbrosseau.freshrssdiscover.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReadingHistogram
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/** Drawing height of the histogram; the tallest bar fills it exactly. */
private val ChartHeight = 160.dp

/**
 * Rounding of a bar's top corners only: the base sits on the axis, and a
 * fully rounded bar would appear to float above it.
 */
private val BarShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

/** Hours labelled under the axis; every hour would be an unreadable comb. */
private val AxisHours = listOf(0, 6, 12, 18, 24)

/**
 * Reading statistics (SPECS.md §6): the histogram behind the reminder hour.
 *
 * The screen exists to show the reminder's reasoning (SPECS.md §4.9). One
 * series, so no legend: the title names it. The dominant hour is carried by
 * the bar's color **and** by the caption below — never by color alone.
 *
 * Stateless, like every screen: it renders [uiState] and nothing else.
 * `null` means the disk has not answered yet, and nothing is drawn — an
 * empty chart would flash before being replaced.
 */
@Composable
fun StatsScreen(uiState: StatsUiState?, modifier: Modifier = Modifier) {
    if (uiState == null) return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.stats_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!uiState.hasData) {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(StatsTestTags.EMPTY),
            )
        } else {
            HourHistogram(uiState = uiState)
            DominantHourCaption(dominantHour = uiState.dominantHour)
        }
    }
}

/**
 * Says in words what the highlighted bar says in color: the dominant hour is
 * never carried by color alone (SPECS.md §7.1).
 */
@Composable
private fun DominantHourCaption(dominantHour: Int?, modifier: Modifier = Modifier) {
    if (dominantHour != null) {
        Text(
            text = stringResource(R.string.stats_dominant, dominantHour),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.testTag(StatsTestTags.DOMINANT),
        )
    } else {
        // Sessions exist but not enough of them (SPECS.md §4.9): saying so
        // beats a highlighted bar the reminder does not follow yet.
        Text(
            text = stringResource(R.string.stats_learning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.testTag(StatsTestTags.LEARNING),
        )
    }
}

/**
 * The 24 bars and their hour axis.
 *
 * Plain boxes rather than a canvas: twenty-four rectangles need no drawing
 * API, and boxes keep test tags and semantics available per bar.
 */
@Composable
private fun HourHistogram(uiState: StatsUiState, modifier: Modifier = Modifier) {
    val chartDescription = uiState.dominantHour
        ?.let { stringResource(R.string.stats_chart_description, it) }
        ?: stringResource(R.string.stats_chart_description_learning)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = chartDescription }
                .testTag(StatsTestTags.CHART),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            uiState.bars.forEach { bar ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // The floor keeps an hour with little weight visible:
                        // a sub-pixel bar would read as an hour never read at.
                        .fillMaxHeight(fraction = bar.fraction.coerceAtLeast(0.02f))
                        .background(
                            color = if (bar.hour == uiState.dominantHour) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = BarShape,
                        )
                        .testTag(StatsTestTags.barOf(bar.hour)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AxisHours.forEach { hour ->
                Text(
                    text = stringResource(R.string.stats_axis_hour, hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenPreview() {
    var histogram = ReadingHistogram.Empty
    for (day in 1L..10L) {
        histogram = histogram.record(day, hour = 8).record(day, hour = 21)
    }

    AppTheme(dynamicColor = false) {
        StatsScreen(uiState = statsUiStateOf(histogram))
    }
}
