package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.ActivityNotFoundException

/** Separator between the scheme and the authority of an absolute URL. */
private const val SCHEME_SEPARATOR = "://"

/**
 * The only schemes that get opened.
 *
 * An RSS feed is untrusted third-party content: its `link` can carry
 * anything. `javascript:`, `file:` or `intent:` do not designate a web page
 * but local execution, a device file, or an arbitrary intent aimed at another
 * app, each letting a remote server decide what the phone does. Only the web
 * is opened.
 */
private val ALLOWED_SCHEMES = setOf("http", "https")

/**
 * Launches the Custom Tab.
 *
 * The interface is deliberately reduced to this one action: it isolates the
 * untestable part (a `Context`, an `Activity` start) so that the decision of
 * which URL deserves to be opened stays testable on the JVM, like
 * `NetworkAvailability`.
 *
 * It exposes no preconnect, preload, or warmup of the Custom Tabs service.
 * Those calls would send requests to a third-party domain before the user
 * even touched the article: an outgoing connection the user never asked for,
 * contrary to SPECS.md §7.4. Nothing leaves before the user's gesture.
 *
 * @throws ActivityNotFoundException if no app handles the intent.
 */
internal fun interface CustomTabLauncher {
    fun launch(url: String)
}

/**
 * Decides whether an article link should be opened, and opens it if so.
 *
 * The screen already makes an article without a link non-clickable
 * (SPECS.md §4.7), but this class does not trust its caller: the security
 * rule only holds if applied at the last moment, where the open happens.
 */
internal class ArticleOpener(private val launcher: CustomTabLauncher) {
    /**
     * Returns nothing: no caller has any behavior to adopt based on the
     * outcome. A rejected link stays silent, and the screen has already made
     * link-less articles non-clickable (AGENTS.md §2).
     *
     * @param url the article's original link, possibly absent.
     */
    fun open(url: String?) {
        val target = url?.trim().orEmpty()

        if (target.isSupportedWebLink()) launchIgnoringAbsentBrowser(target)
    }

    /**
     * A stripped-down device (minimal system image, browser disabled by
     * enterprise policy) has nothing to display a web page, and
     * `startActivity` then throws. Crashing would be the worst response: the
     * gesture is inconsequential, so its failure must be too.
     */
    private fun launchIgnoringAbsentBrowser(url: String) {
        try {
            launcher.launch(url)
        } catch (ignored: ActivityNotFoundException) {
            // Nothing to show: the gesture is inconsequential, so is the failure.
        }
    }
}

/**
 * The authority is required in addition to the scheme: `https:` alone is
 * syntactically valid but designates no page. The comparison is
 * case-insensitive, as schemes are by definition (RFC 3986 §3.1).
 *
 * `internal` rather than `private`: [ArticleSharer] applies the same rule,
 * and duplicating it would let the two copies diverge. A link refused for
 * opening is also refused for handing to another app.
 */
internal fun String.isSupportedWebLink(): Boolean {
    val separator = indexOf(SCHEME_SEPARATOR)
    val scheme = if (separator > 0) take(separator).lowercase() else ""

    return scheme in ALLOWED_SCHEMES && length > separator + SCHEME_SEPARATOR.length
}
