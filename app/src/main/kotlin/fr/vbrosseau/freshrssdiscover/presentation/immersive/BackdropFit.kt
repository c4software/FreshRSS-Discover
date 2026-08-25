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

/** Number of layouts an article can draw among, [BackdropFit.Native] excluded. */
private const val VARIANT_COUNT = 3

/**
 * Decides how a picture is laid on the page.
 *
 * Variety is the point (author's ruling, 2026-08-25): a feed where every
 * page crops its picture the same way reads as one long poster. So the
 * layout is drawn from the **article's id** — stable, so an article keeps
 * its look from one session to the next, and evenly spread, so three
 * consecutive articles rarely share it. The draw ignores the picture's
 * shape on purpose: a landscape banner cropped full screen loses width and
 * reads as zoomed in, and that is one of the looks, not a defect to avoid.
 *
 * One exception: a picture narrower than the page keeps its native size, as
 * on the List card (SPECS.md §8, question 12) — stretched, a thumbnail is
 * not a look, it is a smear. Unknown sizes crop: never blur on a guess.
 *
 * Pure function, outside any `Composable`, so the draw and the exception
 * are asserted rather than eyeballed.
 */
fun backdropFit(articleId: Long, sourceWidthPx: Int, pageWidthPx: Int): BackdropFit {
    val known = sourceWidthPx > 0 && pageWidthPx > 0
    return when {
        !known -> BackdropFit.Full
        sourceWidthPx < pageWidthPx -> BackdropFit.Native
        else -> BackdropFit.entries[Math.floorMod(articleId, VARIANT_COUNT.toLong()).toInt()]
    }
}

/**
 * Tilt of a [BackdropFit.Tilted] picture, in degrees, signed by the article
 * so neighbours lean opposite ways.
 */
fun tiltDegrees(articleId: Long): Float = if (articleId % 2 == 0L) TILT_DEGREES else -TILT_DEGREES

/** Small enough to read as a photograph set down, not as a rendering error. */
private const val TILT_DEGREES = 3f
