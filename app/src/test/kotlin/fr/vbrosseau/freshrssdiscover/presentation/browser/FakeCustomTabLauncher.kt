package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.ActivityNotFoundException

/**
 * Double de [CustomTabLauncher] qui enregistre ce qu'on lui demande d'ouvrir.
 *
 * [launchedUrls] est ce qui permet d'affirmer qu'un lien refusé n'a **rien**
 * déclenché : constater le résultat retourné ne suffirait pas, une intention
 * pourrait partir quand même.
 *
 * @param browserInstalled à faux, reproduit un appareil sans application
 *   capable d'afficher une page web.
 */
internal class FakeCustomTabLauncher(private val browserInstalled: Boolean = true) : CustomTabLauncher {
    val launchedUrls = mutableListOf<String>()

    override fun launch(url: String) {
        if (!browserInstalled) throw ActivityNotFoundException("Aucune application ne gère ACTION_VIEW")

        launchedUrls += url
    }
}
