package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Construit l'intention d'onglet personnalisé.
 *
 * Extraite du lancement pour être vérifiable : le contenu de l'intention est
 * la seule chose qui distingue un onglet personnalisé d'un simple `ACTION_VIEW`.
 *
 * Aucune session n'y est attachée. Une session (`CustomTabsClient.warmup`,
 * `mayLaunchUrl`) ferait démarrer le navigateur et précharger la page dès
 * l'affichage de la liste, donc émettrait des requêtes vers des domaines tiers
 * que l'utilisateur n'a pas sollicités — SPECS.md §7.4. Le prix payé est une
 * ouverture un peu moins rapide ; c'est le bon prix.
 *
 * @param toolbarColor couleur de la barre, fournie par le thème de
 *   l'application pour que l'onglet ne rompe pas avec l'écran qu'il recouvre.
 */
internal fun buildArticleCustomTabsIntent(toolbarColor: Int): CustomTabsIntent = CustomTabsIntent.Builder()
    // Le titre de la page rassure sur la destination, le domaine seul non.
    .setShowTitle(true)
    .setDefaultColorSchemeParams(
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .build(),
    )
    .build()

/**
 * Implémentation Android de [CustomTabLauncher].
 *
 * Elle ne décide de rien : la validité du lien est tranchée en amont par
 * [ArticleOpener]. Elle laisse remonter `ActivityNotFoundException`, que
 * l'appelant sait traduire en résultat observable.
 *
 * @param context contexte d'`Activity` : un contexte d'application obligerait
 *   à poser `FLAG_ACTIVITY_NEW_TASK`, et l'onglet quitterait alors la pile de
 *   l'application — l'utilisateur ne reviendrait pas au flux par « retour ».
 */
internal class AndroidCustomTabLauncher(
    private val context: Context,
    private val toolbarColor: Int,
) : CustomTabLauncher {
    override fun launch(url: String) {
        buildArticleCustomTabsIntent(toolbarColor).launchUrl(context, url.toUri())
    }
}
