package fr.vbrosseau.freshrssdiscover.presentation.browser

/**
 * Presents the system share chooser.
 *
 * Reduced to this one action, like [CustomTabLauncher] and for the same
 * reason: it isolates what cannot be tested (a `Context`, an `Activity`
 * start) so the decision of what gets shared stays testable on the JVM.
 *
 * It receives already-composed text: [ArticleSharer] decides its content, and
 * that decision must be readable in a test without an Android intent.
 */
internal fun interface ArticleShareLauncher {
    fun share(text: String)
}

/**
 * Decides whether an article can be shared, and shares it if so
 * (SPECS.md §4.3).
 *
 * Title then URL, never the excerpt: a bare URL does not say what is being
 * sent, and the excerpt is truncated by the app, so passing it along would
 * share the truncation as if it were content.
 *
 * Same schemes as opening ([isSupportedWebLink]): the link comes from an
 * untrusted third-party feed, and an `intent:` passed to the chooser would be
 * handed as-is to the chosen app. The screen already hides the button on an
 * article without a link (SPECS.md §4.7), but the rule only holds if applied
 * at the last moment, where the share happens.
 *
 * @param textFormat template of the shared text, with two parameters: the
 *   title then the URL. Supplied by the caller, which reads it from
 *   resources: composition stays here and testable, wording stays
 *   translatable.
 */
internal class ArticleSharer(
    private val launcher: ArticleShareLauncher,
    private val textFormat: String,
) {
    /**
     * Returns nothing, like [ArticleOpener.open] and for the same reason: a
     * rejected link stays silent, and the system chooser itself reports when
     * no app can receive text.
     *
     * @param url the article's original link, possibly absent.
     */
    fun share(title: String, url: String?) {
        val target = url?.trim().orEmpty()

        if (target.isSupportedWebLink()) launcher.share(textFormat.format(title, target))
    }
}
