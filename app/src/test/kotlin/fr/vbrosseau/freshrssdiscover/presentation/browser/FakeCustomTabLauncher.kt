package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.ActivityNotFoundException

/**
 * [CustomTabLauncher] fake that records what it is asked to open.
 *
 * [launchedUrls] is what allows asserting that a refused link triggered
 * nothing: checking the returned result would not suffice, an intent could
 * still go out.
 *
 * @param browserInstalled when false, reproduces a device without any app
 *   able to display a web page.
 */
internal class FakeCustomTabLauncher(private val browserInstalled: Boolean = true) : CustomTabLauncher {
    val launchedUrls = mutableListOf<String>()

    override fun launch(url: String) {
        if (!browserInstalled) throw ActivityNotFoundException("Aucune application ne gère ACTION_VIEW")

        launchedUrls += url
    }
}
