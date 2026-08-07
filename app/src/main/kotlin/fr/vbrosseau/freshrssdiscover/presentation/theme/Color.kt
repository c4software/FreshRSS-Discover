package fr.vbrosseau.freshrssdiscover.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette de repli, utilisée lorsque la couleur dynamique Material You n'est
 * pas disponible (API < 31).
 *
 * Volontairement sobre : le flux Discover est fait d'images et de vignettes
 * d'articles, une teinte marquée entrerait en concurrence avec elles.
 */
private val Blue40 = Color(0xFF3A5BC7)
private val Blue80 = Color(0xFFB4C5FF)
private val Slate40 = Color(0xFF575E71)
private val Slate80 = Color(0xFFBFC6DC)

internal val AppLightColorScheme: ColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = Slate40,
)

internal val AppDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = Slate80,
)
