package fr.vbrosseau.freshrssdiscover.presentation.swipe

/**
 * Maximum tilt of a departing card, in degrees.
 *
 * Twelve and no more: the card fills nearly the whole screen height and the
 * pivot sits below it, so one degree of rotation moves the top corner much
 * farther than on the square thumbnail this pattern comes from. At twenty
 * degrees, the title left the frame before the card had crossed half the
 * screen.
 */
private const val MAX_ROTATION_DEGREES = 12f

/**
 * Scale of the card underneath while still fully covered.
 *
 * It must read as a waiting card, not the same card blurred: 0.92 is clearly
 * distinct at the edge without the text looking shrunk when it takes the
 * previous card's place.
 */
private const val DECK_MIN_SCALE = 0.92f

/**
 * Residual opacity of a card reaching the screen edge.
 *
 * It does not drop to zero: a card fully fading before leaving the frame
 * looks like it dissolves in place, whereas the gesture says it is being set
 * aside.
 */
private const val EXIT_MIN_ALPHA = 0.4f

/**
 * Transform to apply to a card so it fits in the stack.
 *
 * @property translationXFraction horizontal offset to add to the one the
 *   pager already applies, as a fraction of a page width.
 * @property drawOrder draw order: the highest value is drawn in front.
 */
data class SwipeCardTransform(
    val translationXFraction: Float,
    val rotationDegrees: Float,
    val scale: Float,
    val alpha: Float,
    val drawOrder: Float,
)

/**
 * Computes the card stack from the pager position alone.
 *
 * [pageOffset] is 0 for the settled card, positive as it leaves to the left,
 * and negative for the one waiting behind; this is the pager convention,
 * `(currentPage - page) + currentPageOffsetFraction`.
 *
 * One symmetric rule: the card with a positive offset is the flying one, the
 * other is the deck. Going forward, the current card leaves left and the next
 * rises behind; going back, the previous one returns from the left onto the
 * top while the current one sinks back into the deck. The same computation
 * renders both directions with no special case.
 *
 * The flying card keeps the pager's translation: the pager moves it off
 * screen, and the finger must feel it follow. The card underneath cancels it
 * instead, to stay centered; otherwise it would slide in from the edge like
 * an ordinary page and there would be no stack, just more scrolling.
 *
 * Pure function, outside any `Composable`: the geometry can be tested without
 * rendering, asserting that no card becomes invisible or flipped mid-gesture
 * (AGENTS.md §9).
 */
fun swipeCardTransform(pageOffset: Float): SwipeCardTransform {
    if (pageOffset < 0f) {
        // The deck: centered, scaled, and behind.
        val revealed = (1f + pageOffset).coerceIn(0f, 1f)
        return SwipeCardTransform(
            translationXFraction = pageOffset,
            rotationDegrees = 0f,
            scale = DECK_MIN_SCALE + (1f - DECK_MIN_SCALE) * revealed,
            alpha = 1f,
            drawOrder = pageOffset,
        )
    }

    val travelled = pageOffset.coerceIn(0f, 1f)
    return SwipeCardTransform(
        translationXFraction = 0f,
        // Negative: the card leaves to the left, so its top tilts left; the
        // opposite tilt would make it look held back.
        rotationDegrees = -travelled * MAX_ROTATION_DEGREES,
        scale = 1f,
        alpha = 1f - (1f - EXIT_MIN_ALPHA) * travelled,
        drawOrder = pageOffset,
    )
}
