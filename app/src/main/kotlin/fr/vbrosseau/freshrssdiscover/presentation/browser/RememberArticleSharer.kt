package fr.vbrosseau.freshrssdiscover.presentation.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.vbrosseau.freshrssdiscover.R

/**
 * Provides a ready-to-use article sharer for a screen.
 *
 * The two strings are read here, not inside the sharer: the sharer is a plain
 * class tested on the JVM, and giving it a `Context` to resolve its own
 * strings would reintroduce the dependency being removed.
 *
 * Remembered on its inputs, like `rememberArticleOpener`.
 */
@Composable
internal fun rememberArticleSharer(): ArticleSharer {
    val context = LocalContext.current
    val textFormat = stringResource(R.string.feed_article_share_text)
    val chooserTitle = stringResource(R.string.feed_article_share_chooser)

    return remember(context, textFormat, chooserTitle) {
        ArticleSharer(
            launcher = AndroidArticleShareLauncher(context = context, chooserTitle = chooserTitle),
            textFormat = textFormat,
        )
    }
}
