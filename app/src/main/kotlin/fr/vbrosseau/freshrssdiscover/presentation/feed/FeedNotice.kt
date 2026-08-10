package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/** Minimum touch target (SPECS.md §7.1). */
private val MinTouchTarget = 48.dp

/**
 * A strip laid over the feed that does not interrupt it.
 *
 * Dismissed manually, never by timer: a message that fades on its own is
 * missed, and the feed's messages all explain something unexpected, such as
 * a refused open or a stale feed.
 *
 * The action colors come from `SnackbarDefaults`: a plain `TextButton` would
 * paint its label in `primary`, a color designed for the background surface,
 * not the strip's inverted one.
 *
 * Test tags stay per screen and arrive via [actionModifier]/[dismissModifier]:
 * both modes have their own, and absorbing them here would conflate them in
 * screen tests.
 *
 * @param dismissLabel optional second command. A notice whose only action is
 *   to fix the situation must still be dismissable: a user unable to fix it
 *   would otherwise have no way out.
 */
@Composable
fun FeedNotice(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissModifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier.padding(Spacing.md),
        action = {
            NoticeAction(label = actionLabel, onClick = onAction, modifier = actionModifier)
        },
        dismissAction = if (dismissLabel != null && onDismiss != null) {
            { NoticeAction(label = dismissLabel, onClick = onDismiss, modifier = dismissModifier) }
        } else {
            null
        },
    ) {
        Text(message)
    }
}

@Composable
private fun NoticeAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionContentColor),
        modifier = modifier.heightIn(min = MinTouchTarget),
    ) {
        Text(label)
    }
}
