package fr.vbrosseau.freshrssdiscover.presentation.immersive

/** How the illustration is laid on a full-screen page (SPECS.md §4.8). */
enum class BackdropFit {
    /** Cropped to fill the page. */
    Full,

    /** Set down whole at the page's width — inset, rounded, shadowed — over a dimmed blur of itself. */
    Framed,

    /** Shown at its own size, over a dimmed blur: the picture is narrower than the page. */
    Native,
}

/**
 * Largest enlargement a picture may take to be shown full screen.
 *
 * Cropped to fill a portrait page, a 16/9 banner is scaled by the page
 * height over its own: a 1080-pixel-tall one is enlarged about 2.2× on a
 * 2340-pixel page, a 720-pixel one 3.3×. Both still read as photographs at
 * arm's length, which is what full screen needs. A 480-pixel thumbnail, at
 * 4.9×, does not; it is framed instead, at the page's width and no larger.
 */
private const val MAX_FULL_UPSCALE = 3.5f

/**
 * Decides how a picture is laid on the page from its size and the page's.
 *
 * Full screen whenever the picture can afford it (author's ruling,
 * 2026-08-25: "c'est vraiment plus beau en full screen"); a draw between
 * looks was tried first and withdrawn. Too small to cover the page without
 * going soft ([MAX_FULL_UPSCALE]), the picture is set down framed; narrower
 * than the page, it keeps its native size, as on the List card (SPECS.md
 * §8, question 12) — stretched, a thumbnail is not a look, it is a smear.
 * Unknown sizes crop: never blur on a guess.
 *
 * Pure function, outside any `Composable`, so the thresholds are asserted
 * rather than eyeballed.
 */
fun backdropFit(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    pageWidthPx: Int,
    pageHeightPx: Int,
): BackdropFit {
    val known = listOf(sourceWidthPx, sourceHeightPx, pageWidthPx, pageHeightPx).all { it > 0 }
    return when {
        !known -> BackdropFit.Full
        sourceWidthPx < pageWidthPx -> BackdropFit.Native
        sourceHeightPx * MAX_FULL_UPSCALE < pageHeightPx -> BackdropFit.Framed
        else -> BackdropFit.Full
    }
}
