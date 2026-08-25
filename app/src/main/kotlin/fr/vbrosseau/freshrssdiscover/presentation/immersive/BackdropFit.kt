package fr.vbrosseau.freshrssdiscover.presentation.immersive

/** How the illustration is laid on a full-screen page (SPECS.md §4.8). */
enum class BackdropFit {
    /** Cropped to fill the page: only for a picture at least as tall, proportionally, as the page. */
    Crop,

    /** Shown whole at the page's width, over a blurred copy that fills the rest. */
    FitWidth,

    /** Shown at its own size, over a blurred copy: the picture is narrower than the page. */
    Native,
}

/**
 * Decides how a picture is laid on the page from its size and the page's.
 *
 * Cropping a landscape photograph to a portrait page throws away most of
 * its width, and what remains reads as zoomed in — the defect observed on
 * device on 2026-08-25. So a picture is cropped only when its aspect ratio
 * is at least as tall as the page's, where the crop takes height alone; a
 * wider one is shown whole at the page's width, on a blurred copy of
 * itself that dresses the bands. A picture narrower than the page keeps its
 * native size, as on the List card (SPECS.md §8, question 12).
 *
 * Pure function, outside any `Composable`, so the three cases are asserted
 * rather than eyeballed.
 *
 * @param sourceWidthPx width of the received image; zero while loading.
 * @param sourceHeightPx height of the received image; zero while loading.
 * @param pageWidthPx measured page width, in screen pixels.
 * @param pageHeightPx measured page height, in screen pixels.
 */
fun backdropFit(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    pageWidthPx: Int,
    pageHeightPx: Int,
): BackdropFit {
    // Unknown sizes crop: never blur on a guess, and the crop is what the
    // page shows anyway until the image arrives.
    val known = listOf(sourceWidthPx, sourceHeightPx, pageWidthPx, pageHeightPx).all { it > 0 }
    return when {
        !known -> BackdropFit.Crop
        sourceWidthPx < pageWidthPx -> BackdropFit.Native
        sourceWidthPx.toFloat() / sourceHeightPx <= pageWidthPx.toFloat() / pageHeightPx -> BackdropFit.Crop
        else -> BackdropFit.FitWidth
    }
}
