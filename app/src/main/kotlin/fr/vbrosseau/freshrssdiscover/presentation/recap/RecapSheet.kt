package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == RecapSheetState.Hidden) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(RecapTestTags.SHEET),
    ) {
        RecapSheetContent(
            state = state,
            onDownloadConfirm = onDownloadConfirm,
            onItemClick = onItemClick,
            onLoadMore = onLoadMore,
        )
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
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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

            is RecapSheetState.Digest -> Digest(state, onItemClick, onLoadMore)

            RecapSheetState.GenerationFailed -> Text(text = stringResource(R.string.recap_failed))
        }
    }
}

/**
 * One card slot per planned summary, all present from the first frame.
 *
 * The layout never grows, it fills (author's call on the third device run):
 * every slot shows as a shimmering skeleton immediately, and each one turns
 * into its summary as its line completes — so the sheet's height is settled
 * before the model has said a word. Still bounded and scrollable for small
 * screens, but with five slots the scroll is the exception.
 */
@Composable
private fun Digest(
    state: RecapSheetState.Digest,
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = DigestMaxHeight)
            .verticalScroll(rememberScrollState())
            .testTag(RecapTestTags.DIGEST),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        state.items.forEachIndexed { index, item ->
            RecapItemCard(
                item = item,
                isBeingWritten = state.isGenerating && index == state.items.lastIndex,
                onItemClick = onItemClick,
            )
        }
        if (state.isGenerating) {
            repeat((state.plannedCount - state.items.size).coerceAtLeast(0)) {
                SkeletonCard()
            }
        }
        if (!state.isGenerating && state.canLoadMore) {
            LoadMorePill(onLoadMore)
        }
    }
}

/**
 * Closes the list when unread articles remain: same tonal language as the
 * cards, but a pill — it is an action, not another summary, and the shape
 * says so before the label does.
 */
@Composable
private fun LoadMorePill(onLoadMore: () -> Unit) {
    Surface(
        onClick = onLoadMore,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecapTestTags.LOAD_MORE),
    ) {
        Text(
            text = stringResource(R.string.recap_load_more),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

/**
 * A summary as a tappable card: the summary is the invitation, the article
 * is the detail, opened like a feed card opens it (SPECS.md §4.10). An
 * unlinked item — the model ignored the format, or the article has no URL —
 * keeps the same card without the tap, like a linkless article in the feed
 * (§4.7).
 */
@Composable
private fun RecapItemCard(
    item: RecapItemUi,
    isBeingWritten: Boolean,
    onItemClick: (String) -> Unit,
) {
    if (item.url != null) {
        Surface(
            onClick = { onItemClick(item.url) },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RecapTestTags.ITEM),
        ) {
            RecapItemBody(item, isBeingWritten)
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RecapTestTags.ITEM),
        ) {
            RecapItemBody(item, isBeingWritten)
        }
    }
}

@Composable
private fun RecapItemBody(
    item: RecapItemUi,
    isBeingWritten: Boolean,
) {
    val summary = remember(item.summary) { digestAnnotated(item.summary) }

    Column(
        modifier = Modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item.title?.let {
            Text(text = it, style = MaterialTheme.typography.titleSmall)
        }
        Text(
            text = summary,
            style = if (isBeingWritten) {
                LocalTextStyle.current.copy(brush = shimmerBrush())
            } else {
                LocalTextStyle.current
            },
        )
    }
}

/**
 * A summary slot whose text has not arrived — the shimmer says "being
 * written" the way every modern feed does, without a spinner.
 */
@Composable
private fun SkeletonCard() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecapTestTags.SKELETON),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonBar(widthFraction = TITLE_BAR_FRACTION)
            SkeletonBar(widthFraction = 1f)
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(SkeletonBarHeight)
            .background(brush = shimmerBrush(), shape = RoundedCornerShape(percent = 50)),
    )
}

/**
 * A highlight sweeping left to right forever — restarting, not reversing:
 * a reversing sweep reads as a glitchy bounce.
 */
@Composable
private fun shimmerBrush(): Brush {
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_SWEEP_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSweep",
    )
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val start = sweep * (SHIMMER_WIDTH_PX + SHIMMER_TRAVEL_PX) - SHIMMER_WIDTH_PX

    return Brush.linearGradient(
        colors = listOf(base.copy(alpha = SHIMMER_DIM_ALPHA), base, base.copy(alpha = SHIMMER_DIM_ALPHA)),
        start = Offset(start, 0f),
        end = Offset(start + SHIMMER_WIDTH_PX, 0f),
    )
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

/**
 * Roughly half a tall screen: enough cards to read comfortably, while the
 * feed behind stays visible enough to remember what the sheet talks about.
 */
private val DigestMaxHeight = 420.dp

private val SkeletonBarHeight = 12.dp
private const val TITLE_BAR_FRACTION = 0.45f
private const val SHIMMER_SWEEP_MILLIS = 1_100
private const val SHIMMER_WIDTH_PX = 400f
private const val SHIMMER_TRAVEL_PX = 900f
private const val SHIMMER_DIM_ALPHA = 0.35f

@Preview
@Composable
private fun RecapSheetContentDigestPreview() {
    AppTheme(dynamicColor = false) {
        RecapSheetContent(
            state = RecapSheetState.Digest(
                items = listOf(
                    RecapItemUi(
                        title = "GNOME 51 en bêta publique",
                        summary = "L'environnement arrive en bêta avec des retouches partout.",
                        url = "https://exemple.org/gnome",
                    ),
                    RecapItemUi(
                        title = "Tensor G6 décortiqué",
                        summary = "Le nouveau processeur gagne surtout en efficacité.",
                        url = "https://exemple.org/tensor",
                    ),
                ),
                plannedCount = 2,
                isGenerating = false,
                canLoadMore = true,
            ),
            onDownloadConfirm = {},
            onItemClick = {},
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
            onItemClick = {},
        )
    }
}
