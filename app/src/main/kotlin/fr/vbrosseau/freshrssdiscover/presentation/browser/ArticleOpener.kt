package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.ActivityNotFoundException

/** Séparateur entre le schéma et l'autorité d'une URL absolue. */
private const val SCHEME_SEPARATOR = "://"

/**
 * Les seuls schémas ouverts.
 *
 * Un flux RSS est du contenu tiers non maîtrisé : son `link` peut porter
 * n'importe quoi. `javascript:`, `file:` ou `intent:` ne désignent pas une page
 * web mais une exécution locale, un fichier de l'appareil ou une intention
 * arbitraire adressée à une autre application — trois façons de laisser un
 * serveur distant décider de ce que fait le téléphone. Seul le web est ouvert.
 */
private val ALLOWED_SCHEMES = setOf("http", "https")

/**
 * Lance l'onglet personnalisé.
 *
 * L'interface est réduite à ce seul geste, volontairement : elle isole la
 * partie non éprouvable (un `Context`, une `Activity` qui démarre) pour que la
 * *décision* — quelle URL mérite d'être ouverte — reste testable en JVM, à la
 * manière de `NetworkAvailability`.
 *
 * Elle **n'expose ni préconnexion, ni préchargement, ni « warmup »** du service
 * Custom Tabs. Ces appels émettraient des requêtes vers un domaine tiers avant
 * même que l'utilisateur ait touché l'article : ce serait une connexion
 * sortante qu'il n'a pas demandée, contraire à SPECS.md §7.4. L'ouverture d'un
 * lien reste une action à son initiative, et rien ne part avant son geste.
 *
 * @throws ActivityNotFoundException si aucune application ne gère l'intention.
 */
internal fun interface CustomTabLauncher {
    fun launch(url: String)
}

/**
 * Décide si un lien d'article doit être ouvert, et l'ouvre le cas échéant.
 *
 * L'écran rend déjà non cliquable un article sans lien (SPECS.md §4.7), mais
 * cette classe ne fait pas confiance à son appelant : la règle de sécurité ne
 * vaut que si elle est appliquée au dernier moment, là où l'ouverture a lieu.
 */
internal class ArticleOpener(private val launcher: CustomTabLauncher) {
    /**
     * Ne rend rien : aucun appelant n'a de conduite à tenir selon l'issue — un
     * lien refusé se tait, et l'écran a déjà rendu non cliquable ce qui n'a pas
     * de lien. Une issue détaillée a existé ici, que tous les appelants
     * jetaient (AGENTS.md §2).
     *
     * @param url le lien d'origine de l'article, éventuellement absent.
     */
    fun open(url: String?) {
        val target = url?.trim().orEmpty()

        if (target.isSupportedWebLink()) launchIgnoringAbsentBrowser(target)
    }

    /**
     * Un appareil dépouillé — image système minimale, navigateur désactivé par
     * une politique d'entreprise — n'a rien pour afficher une page web, et
     * `startActivity` lève alors une exception. Planter sur un lien invalide
     * serait la pire réponse possible : le geste est sans conséquence, l'échec
     * doit l'être aussi.
     */
    private fun launchIgnoringAbsentBrowser(url: String) {
        try {
            launcher.launch(url)
        } catch (ignored: ActivityNotFoundException) {
            // Rien à afficher : le geste est sans conséquence, l'échec aussi.
        }
    }
}

/**
 * L'autorité est exigée en plus du schéma : `https:` seul est une URL valide au
 * sens de la syntaxe, mais ne désigne aucune page. La comparaison est
 * insensible à la casse, un schéma l'étant par définition (RFC 3986 §3.1).
 *
 * `internal` et non `private` : [ArticleSharer] applique **la même** règle, et
 * la dupliquer laisserait les deux copies diverger. Un lien qu'on refuse
 * d'ouvrir est aussi un lien qu'on refuse d'envoyer à une autre application.
 */
internal fun String.isSupportedWebLink(): Boolean {
    val separator = indexOf(SCHEME_SEPARATOR)
    val scheme = if (separator > 0) take(separator).lowercase() else ""

    return scheme in ALLOWED_SCHEMES && length > separator + SCHEME_SEPARATOR.length
}
