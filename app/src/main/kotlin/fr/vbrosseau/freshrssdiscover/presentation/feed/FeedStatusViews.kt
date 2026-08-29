package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.message
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/** Minimum touch target (Material): a lone text button does not always reach it. */
private val MinTouchTarget = 48.dp

/*
 * Terminal states of the feed, written once for both modes.
 *
 * Same rule as `ArticleIllustration`: two copies would diverge at the first
 * fix. What remains with the screens is configuration, their strings and
 * test tags, never the layout.
 */

/**
 * Offline mode, stated calmly (SPECS.md §5.2).
 *
 * `surfaceVariant` rather than `errorContainer`: what the user is looking at
 * works, and painting it in error colors would suggest an app failure. The
 * `surfaceVariant`/`onSurfaceVariant` pair is defined in both themes, which
 * a hand-picked color would not guarantee.
 */
@Composable
internal fun FeedOfflineBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/**
 * The displayed feed is hours old (SPECS.md §4.6).
 *
 * The action reuses the existing refresh rather than opening its own: two
 * paths to the same gesture would diverge. The second command exists for
 * those who do not want to refresh now; without it, the notice could only be
 * silenced by obeying. Strings are shared by both modes (`feed_stale_*`);
 * only the test tags come from the screen.
 */
@Composable
internal fun FeedStaleNotice(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    dismissModifier: Modifier = Modifier,
) {
    FeedNotice(
        message = stringResource(R.string.feed_stale_notice),
        actionLabel = stringResource(R.string.feed_stale_refresh),
        onAction = onRefresh,
        modifier = modifier,
        actionModifier = actionModifier,
        dismissLabel = stringResource(R.string.feed_stale_dismiss),
        onDismiss = onDismiss,
        dismissModifier = dismissModifier,
    )
}

/**
 * A load failed: the message, then its retry (SPECS.md §4.4).
 */
@Composable
internal fun FeedFailureBlock(
    failure: DiscoverFailure,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = failure.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        FeedRetryAction(label = retryLabel, onRetry = onRetry, modifier = retryModifier)
    }
}

/** The retry action alone; the list footer shows it without the message when offline. */
/**
 * A failed page, as the feed's tail states it (SPECS.md §4.4 and §5.2).
 *
 * Offline, the banner has already stated the cause at the top of the feed:
 * repeating it in red under the last article would turn two signals into an
 * alarm while what is displayed still works. Only the retry remains; that is
 * what SPECS.md §4.4 requires, not the color. Decided once for both modes.
 */
@Composable
internal fun FeedFailureOrRetry(
    uiState: FeedUiState,
    failure: DiscoverFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryModifier: Modifier = Modifier,
) {
    if (uiState.showsOfflineBanner) {
        FeedRetryAction(
            label = stringResource(R.string.discover_retry),
            onRetry = onRetry,
            modifier = retryModifier,
        )
    } else {
        FeedFailureBlock(
            failure = failure,
            retryLabel = stringResource(R.string.discover_retry),
            onRetry = onRetry,
            modifier = modifier,
            retryModifier = retryModifier,
        )
    }
}

/**
 * The end of the feed, stated explicitly: a list that stops growing, or a
 * flick that stops responding, is indistinguishable from a breakdown
 * (SPECS.md §4.4). The same words in both modes.
 */
@Composable
internal fun FeedEndOfFeedMessage(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.discover_end_of_feed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(Spacing.lg),
    )
}

@Composable
internal fun FeedRetryAction(
    label: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onRetry,
        modifier = modifier.heightIn(min = MinTouchTarget),
    ) {
        Text(label)
    }
}

/**
 * The feed is exhausted with nothing to show: "you have read everything"
 * under an empty list would explain nothing, hence a message of its own.
 */
@Composable
internal fun FeedEmptyMessage(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.discover_empty_title)
    val body = stringResource(R.string.discover_empty_body)

    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Centers its content full-frame: the loading state and terminal messages. */
@Composable
internal fun FeedCentered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * Runs [onSettled] on the refresh's falling edge, never its rising one.
 *
 * SPECS.md §4.6: the gesture clears and restarts from the top. The screen
 * must return to the top, but only once the new list is in place; going
 * there on press would scroll content about to be discarded.
 */
@Composable
internal fun AfterRefreshSettles(isRefreshing: Boolean, onSettled: suspend () -> Unit) {
    var wasRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) onSettled()
        wasRefreshing = isRefreshing
    }
}
