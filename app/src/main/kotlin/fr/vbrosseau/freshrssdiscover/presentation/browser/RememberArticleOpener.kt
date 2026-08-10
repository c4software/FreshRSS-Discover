package fr.vbrosseau.freshrssdiscover.presentation.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * Provides a ready-to-use article opener for a screen.
 *
 * The toolbar color is read from the current theme, not hardcoded: the color
 * scheme varies with dark mode and dynamic color (`Theme.kt`). `surface`
 * rather than `primary`: the toolbar extends the covered screen's background,
 * it is not an accent element.
 *
 * The opener is remembered on its two inputs: recreating it on each
 * recomposition would have no observable effect but would rebuild the intent
 * needlessly.
 */
@Composable
internal fun rememberArticleOpener(): ArticleOpener {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()

    return remember(context, toolbarColor) {
        ArticleOpener(AndroidCustomTabLauncher(context = context, toolbarColor = toolbarColor))
    }
}
