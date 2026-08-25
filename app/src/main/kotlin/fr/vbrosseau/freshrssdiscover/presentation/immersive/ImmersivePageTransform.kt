package fr.vbrosseau.freshrssdiscover.presentation.immersive

import kotlin.math.absoluteValue

/**
 * Scale of a page that has fully left the viewport.
 *
 * Slight on purpose: the page must read as settling into place, not as a
 * thumbnail growing into a screen. Below 0.9 the text visibly reflows in the
 * eye during the gesture.
 */
private const val MIN_SCALE = 0.94f

/**
 * Opacity of a page that has fully left the viewport.
 *
 * Not zero: the neighbour is already partly on screen while the current page
 * is still mostly there, and a page invisible at the edge would make the
 * scroll look like it reveals a hole rather than the next article.
 */
private const val MIN_ALPHA = 0.55f

/**
 * Share of the page height the backdrop lags behind the page.
 *
 * The parallax is what makes the illustration feel like a scene behind the
 * text rather than a picture glued to it. A quarter is enough to be felt;
 * more, and the top of the image would show its edge mid-gesture.
 */
private const val PARALLAX_FRACTION = 0.25f

/**
 * Transform to apply to an immersive page according to its distance from the
 * settled position.
 *
 * @property scale uniform scale of the whole page.
 * @property alpha opacity of the whole page.
 * @property backdropTranslationYFraction vertical offset of the illustration
 *   alone, as a fraction of the page height, added to the pager's own motion.
 */
data class ImmersivePageTransform(
    val scale: Float,
    val alpha: Float,
    val backdropTranslationYFraction: Float,
)

/**
 * Computes the page motion from the pager position alone.
 *
 * [pageOffset] is 0 for the settled page, positive as it leaves upward, and
 * negative for the one arriving from below; this is the pager convention,
 * `(currentPage - page) + currentPageOffsetFraction`.
 *
 * One symmetric rule, in scale and opacity: the further a page is from the
 * settled position, the smaller and dimmer it is, whichever way it moves.
 * The parallax alone is signed — the backdrop lags behind the page, so it is
 * pushed the way the page came from.
 *
 * Pure function, outside any `Composable`: no screenshot shows the middle of
 * a gesture, so the geometry is asserted here (AGENTS.md §9).
 */
fun immersivePageTransform(pageOffset: Float): ImmersivePageTransform {
    val distance = pageOffset.absoluteValue.coerceIn(0f, 1f)
    return ImmersivePageTransform(
        scale = 1f - (1f - MIN_SCALE) * distance,
        alpha = 1f - (1f - MIN_ALPHA) * distance,
        backdropTranslationYFraction = pageOffset.coerceIn(-1f, 1f) * PARALLAX_FRACTION,
    )
}
