package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Builds the Custom Tab intent.
 *
 * Extracted from the launch so it can be tested: the intent's contents are
 * the only thing distinguishing a Custom Tab from a plain `ACTION_VIEW`.
 *
 * No session is attached. A session (`CustomTabsClient.warmup`,
 * `mayLaunchUrl`) would start the browser and preload the page as soon as the
 * list is shown, sending requests to third-party domains the user never asked
 * for (SPECS.md §7.4). The cost is a slightly slower open.
 *
 * @param toolbarColor toolbar color, taken from the app theme so the tab does
 *   not clash with the screen it covers.
 */
internal fun buildArticleCustomTabsIntent(toolbarColor: Int): CustomTabsIntent = CustomTabsIntent.Builder()
    // The page title reassures about the destination; the domain alone does not.
    .setShowTitle(true)
    .setDefaultColorSchemeParams(
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .build(),
    )
    .build()

/**
 * Android implementation of [CustomTabLauncher].
 *
 * Decides nothing: link validity is settled upstream by [ArticleOpener]. It
 * lets `ActivityNotFoundException` propagate; the caller knows how to turn it
 * into an observable result.
 *
 * @param context an `Activity` context: an application context would require
 *   `FLAG_ACTIVITY_NEW_TASK`, and the tab would leave the app's task stack,
 *   so back navigation would not return to the feed.
 */
internal class AndroidCustomTabLauncher(
    private val context: Context,
    private val toolbarColor: Int,
) : CustomTabLauncher {
    override fun launch(url: String) {
        buildArticleCustomTabsIntent(toolbarColor).launchUrl(context, url.toUri())
    }
}
