package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Requests the next page before the last article is reached (SPECS.md §4.4),
 * written once for the list and the pager.
 *
 * Each mode brings its own position reading and its own distance — five
 * cards, three full-screen pages — but the rule around them is the same:
 *
 * - **Nothing loads before an actual gesture.** The launch does not talk to
 *   the network (SPECS.md §5.1), but the cache filtered of read articles
 *   sometimes fits entirely on screen: the end was then reached without any
 *   gesture and the load fired anyway, reintroducing the request that had
 *   just been removed. Observed on device: the last-server-contact date
 *   still changed on every open. The gesture is detected on position, not on
 *   `isScrollInProgress` alone — a programmatic move counts too — and
 *   latched in an effect, never written during composition.
 * - **Only crossing the threshold matters.** `derivedStateOf` avoids
 *   restarting the effect for every pixel of the gesture.
 * - **The article count is part of the key.** A page shorter than the
 *   distance would otherwise leave the condition true without ever
 *   re-triggering the load, silently stalling the feed.
 *
 * @param positionState the list or pager state, keying the latch: a new
 *   state is a new screen, which has not been moved yet.
 * @param hasMoved snapshot read: the user has entered the feed.
 * @param isNearEnd snapshot read: fewer than the mode's distance remain.
 */
@Composable
internal fun PrefetchNextPage(
    positionState: Any,
    articleCount: Int,
    hasMoved: () -> Boolean,
    isNearEnd: () -> Boolean,
    onLoadMore: () -> Unit,
) {
    var moved by remember(positionState) { mutableStateOf(false) }
    LaunchedEffect(positionState) {
        snapshotFlow(hasMoved).first { it }
        moved = true
    }

    val shouldLoadMore by remember(positionState, articleCount) { derivedStateOf(isNearEnd) }

    LaunchedEffect(shouldLoadMore, articleCount, moved) {
        if (shouldLoadMore && moved) onLoadMore()
    }
}
