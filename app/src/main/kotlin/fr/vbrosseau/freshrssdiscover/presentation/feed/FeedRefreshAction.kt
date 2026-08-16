package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Title-bar slot for the feed refresh action.
 *
 * Appearance and withdrawal are animated: the action comes and goes with the
 * displayed destination, and popping in and out instantly reads as a
 * rendering glitch rather than a state change.
 *
 * The last published action is retained for the exit transition: animating
 * out needs content to draw while [refresh] is already `null`.
 */
@Composable
fun FeedRefreshAction(refresh: FeedRefresh?, modifier: Modifier = Modifier) {
    var lastPublished by remember { mutableStateOf(refresh) }
    if (refresh != null && refresh != lastPublished) lastPublished = refresh

    AnimatedVisibility(
        visible = refresh != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        lastPublished?.let {
            RefreshButton(
                isRefreshing = it.isRefreshing,
                showsProgress = it.showsProgress,
                onRefresh = it.onRefresh,
            )
        }
    }
}
