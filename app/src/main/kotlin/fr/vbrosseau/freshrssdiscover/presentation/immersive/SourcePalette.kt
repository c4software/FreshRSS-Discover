package fr.vbrosseau.freshrssdiscover.presentation.immersive

import androidx.compose.ui.graphics.Color

/** Saturation of the source tint: coloured enough to tell sources apart, never garish. */
private const val SATURATION = 0.55f

/** Lightness of the gradient's two stops, in the light theme: a pastel that keeps `onSurface` readable. */
private const val LIGHT_TOP = 0.86f
private const val LIGHT_BOTTOM = 0.94f

/** Lightness of the gradient's two stops, in the dark theme: deep, so the white text stands out. */
private const val DARK_TOP = 0.22f
private const val DARK_BOTTOM = 0.10f

/** The full hue circle, in degrees. */
private const val HUE_RANGE = 360

/**
 * Backdrop of a page whose article has no illustration (SPECS.md §4.8).
 *
 * @property top colour at the top edge, where the tint is strongest.
 * @property bottom colour at the bottom edge, close to the theme background
 *   so the text block sits on familiar ground.
 * @property monogram the letter drawn as a watermark over the tint.
 */
data class SourcePalette(
    val top: Color,
    val bottom: Color,
    val monogram: String,
)

/**
 * A stable tint for a source, derived from its name alone.
 *
 * The same feed always gets the same hue: after a few sessions the colour
 * becomes the source's signature, which a random draw would never let it
 * be. Lightness follows the theme so `onSurface` keeps its contrast on both.
 *
 * The hash is written out rather than taken from `String.hashCode()`: that
 * one is specified, but a deliberate formula makes the intent visible and
 * keeps the colour from ever depending on a platform detail.
 *
 * Pure function, outside any `Composable`: the mapping is asserted in tests,
 * not eyeballed on screenshots.
 */
fun sourcePalette(feedTitle: String, dark: Boolean): SourcePalette {
    val hue = feedTitle.fold(0) { acc, c -> (acc * HASH_STEP + c.code) % HUE_RANGE }.toFloat()
    val monogram = feedTitle.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString().orEmpty()
    return SourcePalette(
        top = Color.hsl(hue, SATURATION, if (dark) DARK_TOP else LIGHT_TOP),
        bottom = Color.hsl(hue, SATURATION, if (dark) DARK_BOTTOM else LIGHT_BOTTOM),
        monogram = monogram,
    )
}

/** Prime multiplier: spreads neighbouring names across the hue circle. */
private const val HASH_STEP = 31
