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
 * Smallest share of the page height a picture must reach, at the page's
 * width, to be shown full screen.
 *
 * Cropped to fill, a picture is enlarged until it covers the page: below
 * this share the enlargement passes 1.6×, and a photograph at that scale is
 * soft enough to read as a defect rather than a look (author's ruling,
 * 2026-08-25, "si la taille est suffisante"). Such a picture falls back to
 * the framed look, where it is shown at the page's width and no larger.
 */
private const val MIN_FULL_COVERAGE = 0.6f

/**
 * Decides how a picture is laid on the page.
 *
 * Variety is the point (author's ruling, 2026-08-25): a feed where every
 * page crops its picture the same way reads as one long poster. So the
 * layout is drawn from the **article's id** — stable, so an article keeps
 * its look from one session to the next — with the full-screen picture
 * favoured, see [DRAW]. Full screen is only kept for a picture large enough
 * to cover the page ([MIN_FULL_COVERAGE]); a smaller one is framed instead.
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
    val heightAtPageWidth = if (known) sourceHeightPx.toFloat() * pageWidthPx / sourceWidthPx else 0f
    return when {
        !known -> BackdropFit.Full
        sourceWidthPx < pageWidthPx -> BackdropFit.Native
        drawn == BackdropFit.Full && heightAtPageWidth < pageHeightPx * MIN_FULL_COVERAGE -> BackdropFit.Framed
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
