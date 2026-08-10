package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Visibility sampling period, in milliseconds.
 *
 * `ReadDetector` is pure: it never wakes on its own and only measures time
 * between two observations it is given. Without a sampling cadence, an
 * article on screen for ten seconds would never be reported, lacking a second
 * observation after the one that armed the timer (SPECS.md §4.5).
 *
 * 200 ms balances two opposite errors:
 *
 * - Too slow: the crossing instant is known only to within one period, so the
 *   1-second threshold actually fires between 1.0 s and 1.0 s + period. At
 *   200 ms the error stays under 20%, invisible in practice; at 1 s, a read
 *   article could be reported only after 2 s, double the stated threshold.
 * - Too fast: sampling at display frequency (16 ms at 60 Hz) would wake the
 *   coroutine sixty times per second to walk visible items and allocate a
 *   map, while the extra precision changes nothing for a rule whose
 *   threshold is one second. That is battery spent continuously for nothing.
 *
 * 5 Hz is also enough to miss nothing during a scroll: an article that
 * crosses the screen in under 200 ms cannot stay visible for a second anyway.
 */
internal const val VISIBILITY_SAMPLING_PERIOD_MILLIS = 200L

/**
 * Fraction of the article actually displayed, between 0 and 1.
 *
 * SPECS.md §4.5 states that "60% of its height" is measured against the
 * visible part of the screen, not the article's own height: taken literally,
 * an article taller than the viewport could never show 60% of itself and
 * would never become read. The reference is therefore
 * `min(article height, viewport height)`: an article taller than the screen
 * reaches 1.0 as soon as it fills the viewport, exactly the situation where
 * it is being read.
 *
 * A pure function over integers rather than a method on `LazyListLayoutInfo`:
 * this is the only delicate computation of the measurement, and it must be
 * testable without Compose or Android.
 *
 * @param itemOffset position of the article's top, in viewport coordinates.
 * @param itemSize total article height, including its off-screen part.
 * @param viewportStart top edge of the viewport, negative when the list has
 *   content padding.
 * @param viewportEnd bottom edge of the viewport.
 */
internal fun visibleFraction(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float {
    // A zero-height article, or a viewport not yet measured, has no defined
    // fraction: the division would produce a NaN passed to the detector.
    val reference = minOf(itemSize, viewportEnd - viewportStart)
    if (reference <= 0) return 0f

    // Clamp on both sides: the intersection of article and viewport is empty,
    // not negative, once the article is entirely above or below.
    val top = maxOf(itemOffset, viewportStart)
    val bottom = minOf(itemOffset + itemSize, viewportEnd)
    return (bottom - top).coerceAtLeast(0).toFloat() / reference
}

/**
 * Translates the list's layout state into an observation for `ReadDetector`.
 *
 * Only items whose key is an article identifier are kept: the list also
 * carries a footer, whose key is a string, and counting it as a read article
 * would make no sense.
 */
internal fun LazyListLayoutInfo.articleVisibility(): Map<ArticleId, Float> =
    visibleItemsInfo
        .mapNotNull { item ->
            (item.key as? Long)?.let { id ->
                ArticleId(id) to visibleFraction(item.offset, item.size, viewportStartOffset, viewportEndOffset)
            }
        }
        .toMap()

/**
 * Samples visibility for as long as the coroutine lives.
 *
 * The loop observes before waiting: the first observation goes out without
 * delay, otherwise the article displayed when the screen opens would start
 * its timer one period late.
 *
 * It deliberately never stops on its own: the caller owns it. Stopping is a
 * lifecycle fact (the screen goes to the background) that this function has
 * no way to know.
 *
 * @param visibility layout reading, called on each sample rather than
 *   captured once: it is precisely because it changes without notice that it
 *   is polled periodically.
 */
internal suspend fun sampleVisibility(
    periodMillis: Long = VISIBILITY_SAMPLING_PERIOD_MILLIS,
    visibility: () -> Map<ArticleId, Float>,
    onVisibilityChanged: (Map<ArticleId, Float>) -> Unit,
): Unit = coroutineScope {
    while (isActive) {
        onVisibilityChanged(visibility())
        delay(periodMillis)
    }
}
