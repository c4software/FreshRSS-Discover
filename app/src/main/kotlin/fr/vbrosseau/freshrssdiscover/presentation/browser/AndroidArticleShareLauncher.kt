package fr.vbrosseau.freshrssdiscover.presentation.browser

import android.content.Context
import android.content.Intent

/** The only MIME type the share announces: text, not a file. */
private const val PLAIN_TEXT = "text/plain"

/**
 * Builds the share intent, chooser included.
 *
 * Extracted from the launch so it can be tested, like `buildArticleCustomTabsIntent`.
 *
 * `createChooser` rather than the bare intent: without it, Android shows its
 * chooser the first time and then remembers the chosen app, so the second
 * share would leave without asking. The explicit chooser keeps the destination
 * choice with the user every time, which is what guarantees the app commits to
 * no third-party service (SPECS.md §7.4).
 *
 * @param chooserTitle chooser title. Recent Android versions ignore it in
 *   favor of their share sheet, but `minSdk` is 26 and it still shows there.
 */
internal fun buildArticleShareIntent(text: String, chooserTitle: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PLAIN_TEXT
        putExtra(Intent.EXTRA_TEXT, text)
    }

    return Intent.createChooser(send, chooserTitle)
}

/**
 * Android implementation of [ArticleShareLauncher].
 *
 * Decides nothing: what gets shared is settled upstream by [ArticleSharer].
 *
 * @param context an `Activity` context, for the same reason as
 *   `AndroidCustomTabLauncher`: an application context would require
 *   `FLAG_ACTIVITY_NEW_TASK`, and the chooser would leave the app's task
 *   stack.
 */
internal class AndroidArticleShareLauncher(
    private val context: Context,
    private val chooserTitle: String,
) : ArticleShareLauncher {
    override fun share(text: String) {
        context.startActivity(buildArticleShareIntent(text = text, chooserTitle = chooserTitle))
    }
}
