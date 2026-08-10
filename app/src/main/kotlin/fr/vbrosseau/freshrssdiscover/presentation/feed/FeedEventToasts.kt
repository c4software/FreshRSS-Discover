package fr.vbrosseau.freshrssdiscover.presentation.feed

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import fr.vbrosseau.freshrssdiscover.R
import kotlinx.coroutines.flow.Flow

/**
 * Surfaces the engine's one-shot events as toasts.
 *
 * A plain `Toast` rather than a snackbar: the failure block and its Retry
 * already carry the actionable path (SPECS.md §4.4); this only makes the
 * failure noticeable when that block sits below the fold. Collected in a
 * `LaunchedEffect` keyed on the flow, so a recomposition neither replays nor
 * duplicates events.
 */
@Composable
internal fun FeedEventToasts(events: Flow<FeedEvent>) {
    val context = LocalContext.current

    LaunchedEffect(events) {
        events.collect { event ->
            val message = when (event) {
                FeedEvent.ServerUnreachable -> R.string.feed_server_unreachable_toast
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
