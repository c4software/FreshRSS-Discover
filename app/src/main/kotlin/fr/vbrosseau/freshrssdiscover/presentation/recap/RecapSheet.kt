package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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

            is RecapSheetState.Digest ->
                if (state.text.isEmpty()) {
                    GeneratingSpark()
                } else {
                    Digest(state)
                }

            RecapSheetState.GenerationFailed -> Text(text = stringResource(R.string.recap_failed))
        }
    }
}

/**
 * The digest in a bounded viewport that follows the stream.
 *
 * Bounded because an unbounded sheet grows with every chunk and ends up
 * covering the whole feed — seen on the first device run. The viewport
 * sticks to the bottom while generating, so the newest words stay visible;
 * once done it stays where the reader leaves it.
 */
@Composable
private fun Digest(state: RecapSheetState.Digest) {
    val scrollState = rememberScrollState()

    if (state.isGenerating) {
        LaunchedEffect(state.text) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val digest = remember(state.text) { digestAnnotated(state.text) }

    Column(
        modifier = Modifier
            .heightIn(max = DigestMaxHeight)
            .verticalScroll(scrollState),
    ) {
        Text(
            text = if (state.isGenerating) digest.withStreamingCursor() else digest,
            modifier = Modifier.testTag(RecapTestTags.DIGEST),
        )
    }
}

/**
 * The blinking insertion mark at the end of the streaming text — the idiom
 * that says "being written" without reserving a progress-bar row.
 */
@Composable
private fun AnnotatedString.withStreamingCursor(): AnnotatedString {
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CURSOR_BLINK_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    val color = LocalContentColor.current

    return buildAnnotatedString {
        append(this@withStreamingCursor)
        withStyle(SpanStyle(color = color.copy(alpha = blink))) { append(STREAMING_CURSOR) }
    }
}

/** The spark, pulsing while the first words have not arrived. */
@Composable
private fun GeneratingSpark() {
    val pulse by rememberInfiniteTransition(label = "spark").animateFloat(
        initialValue = 1f,
        targetValue = SPARK_PULSE_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPARK_PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkScale",
    )

    Icon(
        painter = painterResource(R.drawable.ic_recap),
        // Decorative: the label below already says what happens.
        contentDescription = null,
        modifier = Modifier
            .size(SparkSize)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
    )
    Text(text = stringResource(R.string.recap_generating))
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
 * Roughly half a tall screen: enough lines to read comfortably, while the
 * feed behind stays visible enough to remember what the sheet talks about.
 */
private val DigestMaxHeight = 360.dp

private val SparkSize = 32.dp
private const val SPARK_PULSE_SCALE = 1.25f
private const val SPARK_PULSE_MILLIS = 600
private const val CURSOR_BLINK_MILLIS = 500
private const val STREAMING_CURSOR = "▍"

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
