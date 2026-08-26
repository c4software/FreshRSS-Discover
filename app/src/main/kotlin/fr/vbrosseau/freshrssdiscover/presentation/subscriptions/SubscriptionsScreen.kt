package fr.vbrosseau.freshrssdiscover.presentation.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionId
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/** Minimum touch target (SPECS.md §7.1); Material 3 draws its buttons at 40 dp. */
private val MinTouchTarget = 48.dp

/**
 * The account's feeds (SPECS.md §6): a field to add one, the list, a bin
 * per row.
 *
 * One lazy list for everything, the form included as its first item: a
 * scrolling column around a lazy list is a nested-scroll conflict, and a
 * fixed form above the list would eat a third of a small screen before the
 * first row.
 *
 * Stateless, like every screen: it renders [uiState] and forwards gestures.
 */
@Composable
fun SubscriptionsScreen(
    uiState: SubscriptionsUiState,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onRemoveRequest: (Subscription) -> Unit,
    onRemoveConfirm: () -> Unit,
    onRemoveDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            AddFeedForm(
                draftUrl = uiState.draftUrl,
                notice = uiState.notice,
                enabled = !uiState.isSubmitting,
                onDraftChange = onDraftChange,
                onAdd = onAdd,
            )
        }

        item { HorizontalDivider() }

        when {
            uiState.isLoading -> item { Loading() }
            uiState.loadFailure != null -> item { LoadFailure(message = uiState.loadFailure, onRetry = onRetry) }
            uiState.subscriptions.isNullOrEmpty() -> item {
                Text(
                    text = stringResource(R.string.subscriptions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SubscriptionsTestTags.EMPTY),
                )
            }

            else -> items(uiState.subscriptions, key = { it.id.value }) { subscription ->
                SubscriptionRow(
                    subscription = subscription,
                    removable = !uiState.isSubmitting,
                    onRemove = { onRemoveRequest(subscription) },
                )
            }
        }
    }

    uiState.removalCandidate?.let { candidate ->
        RemoveConfirmation(subscription = candidate, onConfirm = onRemoveConfirm, onDismiss = onRemoveDismiss)
    }
}

/**
 * The help text, the field and the button, plus the notice of the last
 * action right under them: that is where the eye is after pressing.
 */
@Composable
private fun AddFeedForm(
    draftUrl: String,
    notice: Int?,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.subscriptions_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draftUrl,
            onValueChange = onDraftChange,
            label = { Text(stringResource(R.string.subscriptions_url_label)) },
            placeholder = { Text(stringResource(R.string.subscriptions_url_placeholder)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SubscriptionsTestTags.URL_FIELD),
        )
        Button(
            onClick = onAdd,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .testTag(SubscriptionsTestTags.ADD),
        ) {
            Text(stringResource(R.string.subscriptions_add))
        }
        if (notice != null) {
            Text(
                text = stringResource(notice),
                style = MaterialTheme.typography.bodyMedium,
                // The primary color for every notice, success or failure:
                // the words carry the difference, never the color alone
                // (SPECS.md §7.1), and one color keeps the line readable in
                // both themes.
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(SubscriptionsTestTags.NOTICE),
            )
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
            .testTag(SubscriptionsTestTags.LOADING),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoadFailure(message: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(SubscriptionsTestTags.FAILURE),
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .testTag(SubscriptionsTestTags.RETRY),
        ) {
            Text(stringResource(R.string.subscriptions_retry))
        }
    }
}

/**
 * Title over address, the bin at the end. The address is shown: two feeds
 * of one site often share a title, and the address is what the user typed.
 */
@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    removable: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .testTag(SubscriptionsTestTags.rowOf(subscription.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subscription.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = removable,
            modifier = Modifier
                .size(MinTouchTarget)
                .testTag(SubscriptionsTestTags.removeOf(subscription.id)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.subscriptions_remove, subscription.title),
            )
        }
    }
}

/**
 * Confirmation before removing (SPECS.md §6): the server keeps nothing of
 * an unsubscribed feed, and the bin sits next to every row.
 */
@Composable
private fun RemoveConfirmation(
    subscription: Subscription,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_remove_dialog_title)) },
        text = { Text(stringResource(R.string.subscriptions_remove_dialog_message, subscription.title)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag(SubscriptionsTestTags.REMOVE_CONFIRM),
            ) {
                Text(stringResource(R.string.subscriptions_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .testTag(SubscriptionsTestTags.REMOVE_CANCEL),
            ) {
                Text(stringResource(R.string.subscriptions_remove_cancel))
            }
        },
        modifier = modifier.testTag(SubscriptionsTestTags.REMOVE_DIALOG),
    )
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionsScreenPreview() {
    AppTheme(dynamicColor = false) {
        SubscriptionsScreen(
            uiState = SubscriptionsUiState(
                subscriptions = listOf(
                    Subscription(SubscriptionId(12L), "Le Monde", "https://www.lemonde.fr/rss/une.xml"),
                    Subscription(SubscriptionId(3L), "XKCD", "https://xkcd.com/atom.xml"),
                ),
                notice = R.string.subscriptions_added,
            ),
            onDraftChange = {},
            onAdd = {},
            onRetry = {},
            onRemoveRequest = {},
            onRemoveConfirm = {},
            onRemoveDismiss = {},
        )
    }
}
