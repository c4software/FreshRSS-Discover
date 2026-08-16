package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.Spacing

/**
 * The recap bottom sheet (SPECS.md §4.10) — the project's first: the digest
 * is transient reading over the feed, not a place in the app. A pushed
 * screen would survive the navigation stack and promise a way back to a
 * text that is regenerated at every request.
 *
 * `ModalBottomSheet` is still experimental in Material 3; the opt-in stays
 * local, like the `TopAppBar` one, so the debt remains visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapSheet(
    state: RecapSheetState,
    onDownloadConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == RecapSheetState.Hidden) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(RecapTestTags.SHEET),
    ) {
        RecapSheetContent(state = state, onDownloadConfirm = onDownloadConfirm)
    }
}

/**
 * The sheet's content, split out stateless: the sheet itself renders in a
 * separate window that neither previews nor screenshots can reach.
 */
@Composable
internal fun RecapSheetContent(
    state: RecapSheetState,
    onDownloadConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.recap_title),
            style = MaterialTheme.typography.titleLarge,
        )

        when (state) {
            RecapSheetState.Hidden -> Unit

            RecapSheetState.DownloadOffer -> {
                Text(text = stringResource(R.string.recap_download_offer))
                DownloadButton(onDownloadConfirm)
            }

            is RecapSheetState.Downloading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(
                        R.string.recap_downloading,
                        state.totalBytesDownloaded.toWholeMegabytes(),
                    ),
                )
            }

            RecapSheetState.DownloadFailed -> {
                Text(text = stringResource(R.string.recap_download_failed))
                DownloadButton(onDownloadConfirm)
            }

            RecapSheetState.Empty -> Text(text = stringResource(R.string.recap_empty))

            is RecapSheetState.Digest -> {
                if (state.text.isEmpty()) {
                    // Nothing has arrived yet: the indicator is the content.
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(
                        text = stringResource(R.string.recap_generating),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    Text(
                        text = state.text,
                        modifier = Modifier.testTag(RecapTestTags.DIGEST),
                    )
                    if (state.isGenerating) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            RecapSheetState.GenerationFailed -> Text(text = stringResource(R.string.recap_failed))
        }
    }
}

@Composable
private fun DownloadButton(onDownloadConfirm: () -> Unit) {
    Button(
        onClick = onDownloadConfirm,
        modifier = Modifier.testTag(RecapTestTags.DOWNLOAD),
    ) {
        Text(text = stringResource(R.string.recap_download_action))
    }
}

private const val BYTES_PER_MEGABYTE = 1_048_576L

/** Megabytes for display: raw bytes would read as noise in a progress line. */
private fun Long.toWholeMegabytes(): Int = (this / BYTES_PER_MEGABYTE).toInt()

@Preview
@Composable
private fun RecapSheetContentDigestPreview() {
    AppTheme(dynamicColor = false) {
        RecapSheetContent(
            state = RecapSheetState.Digest(
                text = "• L'actualité du jour tient en deux lignes.\n• Et la seconde est optimiste.",
                isGenerating = false,
            ),
            onDownloadConfirm = {},
        )
    }
}

@Preview
@Composable
private fun RecapSheetContentDownloadOfferPreview() {
    AppTheme(dynamicColor = false) {
        RecapSheetContent(
            state = RecapSheetState.DownloadOffer,
            onDownloadConfirm = {},
        )
    }
}
