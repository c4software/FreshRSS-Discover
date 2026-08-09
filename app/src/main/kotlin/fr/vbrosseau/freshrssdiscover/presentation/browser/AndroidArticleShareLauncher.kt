package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.Context
import android.content.Intent

/** Le seul type que le partage annonce : du texte, pas un fichier. */
private const val PLAIN_TEXT = "text/plain"

/**
 * Construit l'intention de partage, sélecteur compris.
 *
 * Extraite du lancement pour être vérifiable, comme `buildArticleCustomTabsIntent`.
 *
 * **`createChooser` et non l'intention nue.** Sans lui, Android présente son
 * sélecteur la première fois puis retient l'application choisie : le second
 * partage partirait alors sans rien demander. Le sélecteur explicite garde le
 * choix de la destination à l'utilisateur à chaque fois — c'est ce qui fait
 * qu'aucun service tiers n'est engagé par l'application (SPECS.md §7.4).
 *
 * @param chooserTitle titre du sélecteur. Les versions récentes d'Android
 *   l'ignorent au profit de leur feuille de partage, mais `minSdk` est 26 et il
 *   s'affiche encore là-bas.
 */
internal fun buildArticleShareIntent(text: String, chooserTitle: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PLAIN_TEXT
        putExtra(Intent.EXTRA_TEXT, text)
    }

    return Intent.createChooser(send, chooserTitle)
}

/**
 * Implémentation Android de [ArticleShareLauncher].
 *
 * Elle ne décide de rien : ce qui se partage est tranché en amont par
 * [ArticleSharer].
 *
 * @param context contexte d'`Activity`, pour la même raison que
 *   `AndroidCustomTabLauncher` : un contexte d'application obligerait à poser
 *   `FLAG_ACTIVITY_NEW_TASK`, et le sélecteur quitterait la pile de
 *   l'application.
 */
internal class AndroidArticleShareLauncher(
    private val context: Context,
    private val chooserTitle: String,
) : ArticleShareLauncher {
    override fun share(text: String) {
        context.startActivity(buildArticleShareIntent(text = text, chooserTitle = chooserTitle))
    }
}
