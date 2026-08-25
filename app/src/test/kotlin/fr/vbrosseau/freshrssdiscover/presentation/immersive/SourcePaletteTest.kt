package fr.vbrosseau.freshrssdiscover.presentation.immersive

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppDarkColorScheme
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppLightColorScheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** WCAG AA for body text (SPECS.md §7.1). */
private const val MIN_CONTRAST = 4.5

/** Sources whose names are close: the tint must still tell them apart. */
private val SAMPLE_SOURCES = listOf("Le Monde", "Le Monde — Sciences", "Numerama", "Ars Technica", "xkcd")

/**
 * The source tint, tested without rendering: what a screenshot cannot judge
 * is that the tint stays readable for **every** source name, not the two
 * the captures show.
 */
class SourcePaletteTest {

    @Test
    fun theSameSourceAlwaysGetsTheSameTint() {
        // The colour only becomes a signature if it never moves.
        assertEquals(sourcePalette("Le Monde", dark = false), sourcePalette("Le Monde", dark = false))
    }

    @Test
    fun twoSourcesWithCloseNamesGetDifferentTints() {
        assertNotEquals(
            sourcePalette("Le Monde", dark = false).top,
            sourcePalette("Le Monde — Sciences", dark = false).top,
        )
    }

    @Test
    fun theTextKeepsAaContrastOnEveryTintInBothThemes() {
        // SPECS.md §7.1: the tint is a background for `onSurface`, and a
        // pretty hue that swallows the title would be a regression.
        SAMPLE_SOURCES.forEach { source ->
            val light = sourcePalette(source, dark = false)
            val dark = sourcePalette(source, dark = true)
            listOf(light.top, light.bottom).forEach { tint ->
                assertTrue(contrast(AppLightColorScheme.onSurface, tint) >= MIN_CONTRAST, "clair, $source")
            }
            listOf(dark.top, dark.bottom).forEach { tint ->
                assertTrue(contrast(AppDarkColorScheme.onSurface, tint) >= MIN_CONTRAST, "sombre, $source")
            }
        }
    }

    @Test
    fun theMonogramIsTheFirstLetterOrDigitCapitalised() {
        assertEquals("L", sourcePalette("le monde", dark = false).monogram)
        assertEquals("X", sourcePalette("  xkcd", dark = false).monogram)
        assertEquals("2", sourcePalette("« 20 minutes »", dark = false).monogram)
    }

    @Test
    fun aNamelessSourceHasNoMonogramRatherThanAPlaceholder() {
        assertEquals("", sourcePalette("", dark = false).monogram)
        assertEquals("", sourcePalette("…", dark = true).monogram)
    }

    /** WCAG relative-luminance contrast ratio. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
