package fr.vbrosseau.freshrssdiscover.presentation.discover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import kotlinx.coroutines.awaitCancellation
import kotlin.coroutines.EmptyCoroutineContext

/** URL whose loading succeeds, in tests only. */
const val LOADABLE_IMAGE_URL = "https://exemple.org/illustration.jpg"

/** URL whose loading fails, in tests only. */
const val UNREACHABLE_IMAGE_URL = "https://exemple.org/introuvable.jpg"

/** URL whose loading never completes: it freezes the pending state. */
const val PENDING_IMAGE_URL = "https://exemple.org/interminable.jpg"

/**
 * URL whose image is smaller than any slot.
 *
 * The case the blurred backdrop fixes (SPECS.md §4.3): stretched to fill
 * sixteen-ninths of a phone's width, an image this size comes out blurry. It
 * exists here so this case can be captured without the network.
 */
const val TINY_IMAGE_URL = "https://exemple.org/minuscule.jpg"

/**
 * URL whose image is as wide as any page but too short to fill one.
 *
 * The immersive page's framed case (SPECS.md §4.8): wide enough not to be
 * a thumbnail, too few rows to be cropped full screen without going soft.
 */
const val SMALL_IMAGE_URL = "https://exemple.org/petite.jpg"

/** Sides of the small fake image: a wide, short banner. */
private const val SMALL_ILLUSTRATION_WIDTH = 2000
private const val SMALL_ILLUSTRATION_HEIGHT = 400

/** Fake image color: bold, to spot it on a capture. */
private val FakeIllustrationColor = Color.rgb(0x2E, 0x5A, 0x8C)

/** Tiny image colors: two of them, which is essential (see [tinyTwoToneImage]). */
private val TinyIllustrationColor = Color.rgb(0xC0, 0x5A, 0x2E)
private val TinySubjectColor = Color.rgb(0xF2, 0xE8, 0xD5)

/**
 * Side of the fake image, deliberately square.
 *
 * A slot whose height followed the received image would thus measure a square:
 * this is what makes it verifiable that it does not depend on it.
 *
 * Large enough to never be upscaled, which makes it the ordinary case. At
 * 400 px it fell below the width of a phone slot, hence under the blurred
 * backdrop of SPECS.md §4.3, and every capture in the repository would have
 * illustrated the special case while believing to show the general one.
 */
private const val FAKE_ILLUSTRATION_SIDE = 1600

/** Side of the tiny image: well below the width of a real slot. */
private const val TINY_ILLUSTRATION_SIDE = 120

/**
 * A tiny two-tone image, not a flat fill.
 *
 * A flat fill would prove nothing: blurred or sharp, cropped or fitted, it
 * renders exactly the same pixels. A light disc on a dark background makes the
 * three things that matter visible: the subject stays sharp, the backdrop is
 * softened, and the slot is filled to its edges.
 */
private fun tinyTwoToneImage(): coil3.Image {
    val bitmap = createBitmap(TINY_ILLUSTRATION_SIDE, TINY_ILLUSTRATION_SIDE)
    Canvas(bitmap).apply {
        drawColor(TinyIllustrationColor)
        val middle = TINY_ILLUSTRATION_SIDE / 2f
        drawCircle(middle, middle, middle / 2f, Paint().apply { color = TinySubjectColor })
    }
    return bitmap.asImage()
}

/**
 * Installs a deterministic image loader for the duration of a test.
 *
 * No test may depend on the network: `FakeImageLoaderEngine` answers in place
 * of the real loader, immediately and always identically. Anything that is not
 * [LOADABLE_IMAGE_URL] fails, which is how load failure, not an exotic case on
 * public feeds, becomes reproducible.
 *
 * `coroutineContext` is emptied of any dispatcher: the request then runs on
 * the calling thread. Without this the result would arrive after the capture,
 * and the test would observe the loading rather than its outcome.
 */
@OptIn(DelicateCoilApi::class)
fun installFakeImageLoader() {
    val illustration = ColorImage(
        color = FakeIllustrationColor,
        width = FAKE_ILLUSTRATION_SIDE,
        height = FAKE_ILLUSTRATION_SIDE,
    )
    val tiny = tinyTwoToneImage()
    val small = ColorImage(
        color = FakeIllustrationColor,
        width = SMALL_ILLUSTRATION_WIDTH,
        height = SMALL_ILLUSTRATION_HEIGHT,
    )
    val engine = FakeImageLoaderEngine.Builder()
        .intercept({ it.toString() == LOADABLE_IMAGE_URL }, illustration)
        .intercept({ it.toString() == TINY_IMAGE_URL }, tiny)
        .intercept({ it.toString() == SMALL_IMAGE_URL }, small)
        .intercept({ it.toString() == PENDING_IMAGE_URL }) { awaitCancellation() }
        .default(
            Interceptor { chain ->
                ErrorResult(
                    image = null,
                    request = chain.request,
                    throwable = IllegalStateException("illustration indisponible"),
                )
            },
        )
        .build()

    val context = ApplicationProvider.getApplicationContext<Context>()
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context)
            .components { add(engine) }
            .coroutineContext(EmptyCoroutineContext)
            .build(),
    )
}

/** Restores the image loader to its original state, so nothing leaks between tests. */
@OptIn(DelicateCoilApi::class)
fun resetImageLoader() {
    SingletonImageLoader.reset()
}
