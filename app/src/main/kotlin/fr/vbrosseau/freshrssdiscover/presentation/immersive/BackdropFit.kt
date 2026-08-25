package fr.vbrosseau.freshrssdiscover.presentation.immersive

/** How the illustration is laid on a full-screen page (SPECS.md §4.8). */
enum class BackdropFit {
    /** Cropped to fill the page. */
    Full,

    /** Shown whole at the page's width, over a blurred copy that fills the rest. */
    Framed,

    /** Shown whole, slightly tilted with a shadow, over a blurred copy. */
    Tilted,

    /** Shown at its own size, over a blurred copy: the picture is narrower than the page. */
    Native,
}

/**
 * The draw, as a table: an article's id picks a slot.
 *
 * Three full-screen slots out of five (author's ruling, 2026-08-25): the
 * picture filling the page is the mode's promise, the two other looks are
 * the relief that keeps the feed from reading as one long poster. Spread
 * out rather than grouped, so two consecutive articles seldom share a look.
 */
private val DRAW = listOf(
    BackdropFit.Full,
    BackdropFit.Framed,
    BackdropFit.Full,
    BackdropFit.Tilted,
    BackdropFit.Full,
)

/**
 * Largest enlargement a picture may take to be shown full screen.
 *
 * Cropped to fill a portrait page, a 16/9 banner is scaled by the page
 * height over its own: a 1080-pixel-tall one is enlarged about 2.2× on a
 * 2340-pixel page, a 720-pixel one 3.3×. Both still read as photographs at
 * arm's length, which is what full screen needs (author's ruling,
 * 2026-08-25: the feed's pictures are 16/9 and must go full screen).
 * A 480-pixel thumbnail, at 4.9×, does not; it is framed instead, at the
 * page's width and no larger.
 */
private const val MAX_FULL_UPSCALE = 3.5f

/**
 * Decides how a picture is laid on the page.
 *
 * Variety is the point (author's ruling, 2026-08-25): a feed where every
 * page crops its picture the same way reads as one long poster. So the
 * layout is drawn from the **article's id** — stable, so an article keeps
 * its look from one session to the next — with the full-screen picture
 * favoured, see [DRAW]. Full screen is only kept for a picture with enough
 * pixels to cover the page ([MAX_FULL_UPSCALE]); a smaller one is framed.
 *
 * One more exception: a picture narrower than the page keeps its native
 * size, as on the List card (SPECS.md §8, question 12) — stretched, a
 * thumbnail is not a look, it is a smear. Unknown sizes crop: never blur on
 * a guess.
 *
 * Pure function, outside any `Composable`, so the draw and the exceptions
 * are asserted rather than eyeballed.
 */
fun backdropFit(
    articleId: Long,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    pageWidthPx: Int,
    pageHeightPx: Int,
): BackdropFit {
    val known = listOf(sourceWidthPx, sourceHeightPx, pageWidthPx, pageHeightPx).all { it > 0 }
    val drawn = DRAW[Math.floorMod(articleId, DRAW.size.toLong()).toInt()]
    return when {
        !known -> BackdropFit.Full
        sourceWidthPx < pageWidthPx -> BackdropFit.Native
        drawn == BackdropFit.Full && sourceHeightPx * MAX_FULL_UPSCALE < pageHeightPx -> BackdropFit.Framed
        else -> drawn
    }
}

/**
 * Tilt of a [BackdropFit.Tilted] picture, in degrees, signed by the article
 * so neighbours lean opposite ways.
 */
fun tiltDegrees(articleId: Long): Float = if (articleId % 2 == 0L) TILT_DEGREES else -TILT_DEGREES

/** Small enough to read as a photograph set down, not as a rendering error. */
private const val TILT_DEGREES = 3f
