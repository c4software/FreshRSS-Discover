package fr.vbrosseau.freshrssdiscover.presentation.browser

/**
 * Présente le sélecteur de partage du système.
 *
 * Réduite à ce seul geste, comme [CustomTabLauncher] et pour la même raison :
 * elle isole ce qui n'est pas éprouvable — un `Context`, une `Activity` qui
 * démarre — pour que la *décision*, ce qui se partage et ce qui se tait, reste
 * testable en JVM.
 *
 * Elle reçoit le texte **déjà composé** : c'est [ArticleSharer] qui décide de
 * son contenu, et cette décision doit se lire dans un test sans passer par une
 * intention Android.
 */
internal fun interface ArticleShareLauncher {
    fun share(text: String)
}

/**
 * Décide si un article peut être partagé, et le partage le cas échéant
 * (SPECS.md §4.3).
 *
 * **Le titre puis l'URL, jamais l'extrait.** Une URL nue ne dit pas ce qu'on
 * envoie ; l'extrait, lui, est écourté par nous, et le transmettre partagerait
 * notre troncature pour du contenu.
 *
 * **Les mêmes schémas que l'ouverture** ([isSupportedWebLink]) : le lien vient
 * d'un flux tiers non maîtrisé, et un `intent:` passé au sélecteur serait remis
 * tel quel à l'application choisie. L'écran masque déjà le bouton d'un article
 * sans lien (SPECS.md §4.7), mais la règle ne vaut que si elle est appliquée au
 * dernier moment, là où le partage a lieu.
 *
 * @param textFormat gabarit du texte partagé, à deux paramètres — le titre puis
 *   l'URL. Fourni par l'appelant, qui le lit dans les ressources : la
 *   composition reste ici, éprouvable, mais la formulation reste traduisible.
 */
internal class ArticleSharer(
    private val launcher: ArticleShareLauncher,
    private val textFormat: String,
) {
    /**
     * Ne rend rien, comme [ArticleOpener.open] et pour la même raison : un
     * lien refusé se tait, et le sélecteur du système dit lui-même quand
     * aucune application ne sait recevoir du texte.
     *
     * @param url le lien d'origine de l'article, éventuellement absent.
     */
    fun share(title: String, url: String?) {
        val target = url?.trim().orEmpty()

        if (target.isSupportedWebLink()) launcher.share(textFormat.format(title, target))
    }
}
