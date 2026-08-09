package fr.vbrosseau.freshrssdiscover.presentation.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.vbrosseau.freshrssdiscover.R

/**
 * Fournit le partageur d'article prêt à l'emploi pour un écran.
 *
 * Les deux textes sont lus **ici** et non dans le partageur : celui-ci est une
 * classe ordinaire, éprouvée en JVM, et lui donner un `Context` pour résoudre
 * ses propres chaînes lui rendrait ce qu'on cherche à lui retirer.
 *
 * Mémorisé sur ses entrées, comme `rememberArticleOpener` : le recréer à chaque
 * recomposition serait sans effet observable, mais pour rien.
 */
@Composable
internal fun rememberArticleSharer(): ArticleSharer {
    val context = LocalContext.current
    val textFormat = stringResource(R.string.feed_article_share_text)
    val chooserTitle = stringResource(R.string.feed_article_share_chooser)

    return remember(context, textFormat, chooserTitle) {
        ArticleSharer(
            launcher = AndroidArticleShareLauncher(context = context, chooserTitle = chooserTitle),
            textFormat = textFormat,
        )
    }
}
