package fr.vbrosseau.freshrssdiscover.presentation.recap

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
 * Title-bar slot for the recap action, mirror of `FeedRefreshAction`.
 *
 * Same animation and same retention of the last published action for the
 * exit transition: the two buttons sit side by side, and one popping while
 * the other fades would read as a glitch.
 */
@Composable
fun FeedRecapAction(recap: FeedRecap?, modifier: Modifier = Modifier) {
    var lastPublished by remember { mutableStateOf(recap) }
    if (recap != null && recap != lastPublished) lastPublished = recap

    AnimatedVisibility(
        visible = recap != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        lastPublished?.let {
            RecapButton(onRecap = it.onRecap)
        }
    }
}
