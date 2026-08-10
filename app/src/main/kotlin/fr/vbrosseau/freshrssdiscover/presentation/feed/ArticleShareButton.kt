package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.vbrosseau.freshrssdiscover.R

/** Minimum touch target (SPECS.md §7.1): Material stops at 40 dp. */
private val MinTouchTarget = 48.dp

/**
 * Glyph size, distinct from the target size.
 *
 * 16 dp: the button is not the subject of either card, and larger sizes
 * weighed too heavily on screen. 16 dp stays above the cap height of
 * `labelMedium`, so it remains legible.
 *
 * The touch target does not follow. SPECS.md §7.1 fixes 48 dp, and "more
 * compact" cannot mean "harder to touch". The two measures are therefore
 * separated, which is exactly what `IconButton` allows: it centers its
 * content within its target.
 */
private val GlyphSize = 16.dp

/**
 * Shares the article's link (SPECS.md §4.3).
 *
 * A single component for both modes, like [RefreshButton] and for the same
 * reason: it is the same action, and two implementations would diverge at
 * the first one-sided adjustment.
 *
 * The caller does not place it when the article has no link. The button does
 * not decide its own absence: there is nothing to announce to a screen
 * reader, and a grayed-out command would say "not now" where the answer is
 * "never for this article".
 *
 * An icon alone, with its description. A label would be redundant on a card
 * with a single command, and in List mode it would take the place of a line
 * of text on every article. The description is what makes the command
 * announceable (SPECS.md §7.1).
 *
 * Two sizes measuring different things: [MinTouchTarget] for what the finger
 * reaches, [GlyphSize] for what the eye sees. Conflating them would force a
 * choice between an icon crushing the card footer and a target too small.
 *
 * @param testTag specific to the article, as for [ArticleIllustration]: in
 *   List mode there are as many buttons as displayed cards, and a shared tag
 *   would designate nothing.
 */
@Composable
fun ArticleShareButton(
    onShare: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onShare,
        modifier = modifier
            .size(MinTouchTarget)
            .testTag(testTag),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_share),
            contentDescription = stringResource(R.string.feed_article_share),
            modifier = Modifier.size(GlyphSize),
        )
    }
}
