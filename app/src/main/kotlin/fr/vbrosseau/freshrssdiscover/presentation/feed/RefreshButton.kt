package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R

/** Minimum touch target (SPECS.md §7.1): Material stops at 40 dp. */
private val MinTouchTarget = 48.dp

/** Diameter of the indicator replacing the icon: same as the icon. */
private val IndicatorSize = 24.dp

/**
 * Indicator stroke width.
 *
 * The default is calibrated for a 40 dp indicator; at 24 dp it produces a
 * thick ring that reads as a solid disc.
 */
private val IndicatorStroke = 2.dp

/**
 * Refreshes the feed without a pull gesture (SPECS.md §4.6).
 *
 * One component for both presentation modes: the action is identical (clear,
 * reload, return to the top) and two implementations would diverge at the
 * first one-sided adjustment.
 *
 * A button is needed even though List mode has a pull gesture: in full screen
 * there is no list to pull, and overlaying a vertical pull on the horizontal
 * swipe would create two competing gestures on the same surface. It also
 * serves List mode in addition to the gesture, because a pull is not usable
 * by everyone (SPECS.md §7.1) and no other control replaced it.
 *
 * It sits on the title row rather than over the content: it is a whole-screen
 * command, and overlaying it on the feed would always cover part of it. A
 * bare `IconButton` then suffices: the bar provides a solid background, where
 * a filled background would have been needed to stay legible over an
 * arbitrary image.
 *
 * It turns into an indicator while refreshing rather than dimming or
 * disappearing: a dimmed button reads as "unavailable" rather than "in
 * progress", and a vanishing button leaves the user unsure the tap was
 * registered. It also stays inert while the refresh lasts: `onClick` is
 * unwired, making a double tap ineffective here in addition to the ViewModel
 * guard.
 */
@Composable
fun RefreshButton(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { if (!isRefreshing) onRefresh() },
        modifier = modifier
            .size(MinTouchTarget)
            .testTag(RefreshTestTags.BUTTON),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(IndicatorSize),
                strokeWidth = IndicatorStroke,
                color = LocalContentColor.current,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = stringResource(R.string.feed_refresh),
            )
        }
    }
}
