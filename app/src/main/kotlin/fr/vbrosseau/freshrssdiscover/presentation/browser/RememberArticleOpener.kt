package fr.vbrosseau.freshrssdiscover.presentation.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * Fournit l'ouvreur d'article prêt à l'emploi pour un écran.
 *
 * La couleur de barre est lue dans le thème courant, et non figée : le schéma
 * de couleurs varie avec le mode sombre et avec la couleur dynamique
 * (`Theme.kt`). `surface` plutôt que `primary` — la barre prolonge l'arrière-plan
 * de l'écran recouvert, elle n'est pas un élément d'accentuation.
 *
 * L'ouvreur est mémorisé sur ses deux entrées : le recréer à chaque
 * recomposition serait sans effet observable, mais reconstruirait l'intention
 * pour rien.
 */
@Composable
internal fun rememberArticleOpener(): ArticleOpener {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()

    return remember(context, toolbarColor) {
        ArticleOpener(AndroidCustomTabLauncher(context = context, toolbarColor = toolbarColor))
    }
}
