package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlin.math.absoluteValue

/**
 * Translates the pager position into an observation for `ReadDetector`
 * (SPECS.md §4.5 and §4.8, GOAL-012-T01).
 *
 * A mode-specific function: List mode reads visibility from
 * `LazyListState.layoutInfo`, with multiple articles on screen each with its
 * visible fraction. Here there is no lazy list or layout to walk, only a
 * pager position (an index and an offset), which alone says what the user
 * sees.
 *
 * Offset rather than the settled page: `settledPage` would credit the
 * previous article for the whole gesture while it is already half off
 * screen. The offset gives the same answer as List mode: the current page
 * occupies `1 - |offset|` of the screen, its neighbour the rest. A settled
 * article is thus 1.0, so the area threshold of SPECS.md §4.5 is met
 * immediately and duration alone decides, as §4.8 states. During a slow
 * swipe neither page stays above 60% long enough, so the gesture marks
 * nothing, which is the intended behavior.
 *
 * Pure function over ints and a float, outside any Composable: it is the only
 * delicate part of the measurement and must be testable without Compose or
 * Android, like `visibleFraction` in List mode.
 *
 * @param articleIds article ids in swipe order. The index past the last one
 *   is the end-of-feed page: it is not an article and nothing is timed there.
 * @param currentPage index of the pager's current page.
 * @param currentPageOffsetFraction offset of the current page, in `]-1, 1[`:
 *   positive toward the next article, negative toward the previous one.
 */
internal fun pagerVisibility(
    articleIds: List<Long>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): Map<ArticleId, Float> {
    val current = articleIds.getOrNull(currentPage) ?: return emptyMap()

    val offset = currentPageOffsetFraction.absoluteValue.coerceIn(0f, 1f)
    val neighbour = if (currentPageOffsetFraction > 0f) currentPage + 1 else currentPage - 1

    return buildMap {
        put(ArticleId(current), 1f - offset)
        // The neighbour only exists mid-gesture, and not always: at both ends
        // of the feed and toward the trailing page there is no article.
        if (offset > 0f) articleIds.getOrNull(neighbour)?.let { put(ArticleId(it), offset) }
    }
}
