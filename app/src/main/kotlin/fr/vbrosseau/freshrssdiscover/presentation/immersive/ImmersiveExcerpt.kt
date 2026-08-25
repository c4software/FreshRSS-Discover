package fr.vbrosseau.freshrssdiscover.presentation.immersive

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.toUiModel
import fr.vbrosseau.freshrssdiscover.presentation.feed.truncatedAtWord

/**
 * Maximum excerpt length in Immersive mode (SPECS.md §8, question 8).
 *
 * List mode stops at 240 characters, calibrated for a card's three lines.
 * Full screen has no such constraint; the value is set at 1,400 characters,
 * cut on a word boundary as in List mode.
 *
 * Two measurements decided the figure:
 *
 * - What the screen holds: at 411 dp, a `bodyLarge` line carries about 48
 *   characters over 24 dp of height. Below a 16/9 illustration, the source
 *   line, and the title, about twenty lines remain (roughly 1,100
 *   characters); without an illustration, about thirty (roughly 1,500).
 *   1,400 fills the screen in both cases with at most a short scroll.
 * - What articles do: the median summary is 1,324 characters (SPECS.md §8,
 *   question 7). At 1,400, the ordinary article is shown whole: truncation
 *   becomes the exception, reserved for feeds publishing outsized summaries,
 *   the exact inverse of List mode where it is the rule.
 *
 * Why not the whole summary the server sends anyway:
 *
 * - It is not the article. SPECS.md §4.7 opens the original link in the
 *   browser; the feed provides a summary, often the first half of a text cut
 *   without regard for meaning. Showing it whole would pass off a mid-
 *   sentence stop as a complete read and remove any reason to open the
 *   article.
 * - The cost would be unbounded. The measured maximum is 34,777 characters.
 *   Without a cap, Compose would measure that paragraph on every
 *   recomposition, for a page dismissed with one gesture. 1,400 caps the
 *   cost at 4%.
 */
const val IMMERSIVE_EXCERPT_MAX_LENGTH = 1_400

/**
 * Projects a domain article into its full-screen displayable form.
 *
 * Built on top of the List mode projection rather than beside it: everything
 * is identical (title, source, age, illustration, link) except the excerpt
 * length, the only thing full screen changes (SPECS.md §4.8).
 *
 * @param nowEpochMillis reference instant, provided by `Clock`.
 */
fun Article.toImmersiveUiModel(nowEpochMillis: Long): ArticleUiModel =
    toUiModel(nowEpochMillis).copy(excerpt = summary.toImmersiveExcerpt())

/** Word-boundary truncation lives in `truncatedAtWord`, shared with List mode. */
private fun String.toImmersiveExcerpt(): String = truncatedAtWord(IMMERSIVE_EXCERPT_MAX_LENGTH)
