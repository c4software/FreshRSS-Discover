package fr.vbrosseau.freshrssdiscover.presentation.feed

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * Aspect ratio of the illustration slot.
 *
 * Fixed, never derived from the received image: a height following the image
 * would change when it arrives, and content would jump under the finger. 16/9
 * is the most common article banner format, so the one that crops the least.
 */
private const val ILLUSTRATION_ASPECT_RATIO = 16f / 9f

/**
 * Opacity of the tint marking the slot while loading.
 *
 * Applied to `onSurface`, the color opposite the background: it darkens in
 * light theme and lightens in dark theme. `surfaceVariant` blends into the
 * background in light theme; an image placeholder with contrast 1.00 has
 * already shipped in this repository that way.
 */
private const val ILLUSTRATION_PLACEHOLDER_ALPHA = 0.12f

/**
 * Blur radius of the background.
 *
 * Wide enough that the subject is no longer readable (a background where the
 * scene is still discernible competes with the sharp image on top), but not
 * so wide as to flatten the tint, which is what ties the background to the
 * image.
 */
private val BLUR_RADIUS = 24.dp

/**
 * Overscan of the blurred copy beyond the slot.
 *
 * `blur` fades out toward the edges: without this slight enlargement, the
 * slot tint would show around the border, reintroducing the frame the blur is
 * meant to remove.
 */
private const val BLUR_OVERSCAN = 1.1f

/**
 * An article's illustration, shared by both modes (SPECS.md §4.3).
 *
 * Decorative, no description (SPECS.md §7.1): the feed provides no alt text,
 * and a description forged on the spot would add a node to traverse without
 * teaching anything.
 *
 * A load failure collapses the slot rather than leaving a tinted frame: an
 * image that cannot be fetched is indistinguishable, for the reader, from an
 * article that has none.
 *
 * @param testTag tag specific to the calling screen: both modes have their
 *   own, and absorbing them here would conflate them in screen tests.
 */
@Composable
fun ArticleIllustration(
    imageUrl: String?,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = imageUrl, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()

    if (imageUrl == null || state is AsyncImagePainter.State.Error) return

    var slotWidthPx by remember { mutableIntStateOf(0) }
    val sourceWidthPx = (state as? AsyncImagePainter.State.Success)?.result?.image?.width ?: 0
    val blurred = supportsBlur && needsUpscaling(sourceWidthPx = sourceWidthPx, slotWidthPx = slotWidthPx)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ILLUSTRATION_ASPECT_RATIO)
            // Painted under the image, this tint is only visible while there
            // is nothing to show: it marks the reserved slot without
            // pretending to be an illustration.
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = ILLUSTRATION_PLACEHOLDER_ALPHA))
            .onSizeChanged { slotWidthPx = it.width }
            .testTag(testTag),
    ) {
        if (blurred) {
            /*
             * The same image, cropped to fill and blurred: the slot stays
             * full, with no empty band or frame, and the background always
             * matches the subject since it comes from it. Preferable to a
             * dominant color, which would require reading pixels, a
             * per-image computation.
             *
             * The blur is applied to a copy enlarged beyond the slot
             * (`scale`): otherwise the blur's edge fade would let the
             * background show through and reintroduce the frame.
             */
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(BLUR_OVERSCAN)
                    .blur(BLUR_RADIUS),
                contentScale = ContentScale.Crop,
            )
        }

        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            /*
             * `Inside` rather than `Fit`: `Fit` fills the slot's smaller
             * dimension and therefore still upscales, leaving the foreground
             * image blurry (observed on device). `Inside` shrinks what
             * overflows but never grows past native size: the only scale
             * that guarantees a sharp image, since it invents no pixels.
             */
            contentScale = if (blurred) ContentScale.Inside else ContentScale.Crop,
        )
    }
}

/**
 * `Modifier.blur` only takes effect from Android 12 (API 31); the project
 * goes down to 26.
 *
 * Below that, nothing changes: the image stays stretched. A clean
 * degradation, preferred over a second mechanism, which would leave the
 * background sharp and duplicated, worse than the flaw being fixed.
 */
private val supportsBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
