package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
        // No half-height stop: with it, the first back only shrank the sheet
        // and a second was needed to close — seen on device, twice.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag(RecapTestTags.SHEET),
    ) {
        // Inside the sheet's own window, so it owns the back gesture: seen
        // on device, back reached the navigation behind and left the sheet
        // hanging over the wrong screen.
        BackHandler(onBack = onDismiss)
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

            is RecapSheetState.Digest -> Brief(state, onItemClick, onLoadMore)

            RecapSheetState.GenerationFailed -> Text(text = stringResource(R.string.recap_failed))
        }
    }
}

/**
 * The brief: one flowing paragraph telling what happened across the batch,
 * its article-bound passages underlined and tappable (author's call on the
 * seventh device run — per-article cards only paraphrased the list). While
 * the prose streams, its tail shimmers; before the first words, skeleton
 * lines hold the paragraph's place.
 */
@Composable
private fun Brief(
    state: RecapSheetState.Digest,
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val scrollState = rememberScrollState()

    // A batch replaces the paragraph wholesale: without this, the viewport
    // would stay where the previous batch's "load more" pill was.
    LaunchedEffect(state.isGenerating) {
        if (state.isGenerating && state.segments.isEmpty()) scrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .height(DigestHeight)
            .verticalScroll(scrollState)
            .testTag(RecapTestTags.DIGEST),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.segments.isEmpty() && state.isGenerating) {
            BriefSkeleton()
        } else {
            Text(
                text = briefAnnotated(
                    segments = state.segments,
                    linkColor = MaterialTheme.colorScheme.primary,
                    onSegmentClick = onItemClick,
                    tailBrush = if (state.isGenerating) shimmerBrush() else null,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (!state.isGenerating && state.canLoadMore) {
            LoadMorePill(onLoadMore)
        }
    }
}

/**
 * Paragraph-shaped placeholder — full lines and a short last one — so the
 * wait already has the silhouette of what will replace it.
 */
@Composable
private fun BriefSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(SKELETON_FULL_LINES) { SkeletonBar(widthFraction = 1f) }
        SkeletonBar(widthFraction = LAST_LINE_FRACTION)
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

@Composable
private fun SkeletonBar(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(SkeletonBarHeight)
            .background(brush = shimmerBrush(), shape = RoundedCornerShape(percent = 50))
            .testTag(RecapTestTags.SKELETON),
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
 * Fixed, not a maximum (author's call, GOAL-037-T16): the sheet must not
 * resize as the prose streams or as "load more" swaps the batch. Roughly
 * half a tall screen, the feed behind staying visible enough to remember
 * what the sheet talks about.
 */
private val DigestHeight = 420.dp

private val SkeletonBarHeight = 12.dp
private const val SKELETON_FULL_LINES = 3
private const val LAST_LINE_FRACTION = 0.6f
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
                segments = listOf(
                    RecapSegmentUi(text = "La semaine s'ouvre sur la bêta publique de ", url = null),
                    RecapSegmentUi(text = "GNOME 51", url = "https://exemple.org/gnome"),
                    RecapSegmentUi(text = ", pendant que le ", url = null),
                    RecapSegmentUi(text = "Tensor G6", url = "https://exemple.org/tensor"),
                    RecapSegmentUi(text = " gagne surtout en efficacité énergétique.", url = null),
                ),
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
