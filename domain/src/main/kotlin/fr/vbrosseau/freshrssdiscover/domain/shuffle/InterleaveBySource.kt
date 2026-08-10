package fr.vbrosseau.freshrssdiscover.domain.shuffle

import fr.vbrosseau.freshrssdiscover.domain.feed.Article

/**
 * Reordering scope, in number of articles.
 *
 * This value defines what "shuffle locally" means (SPECS.md §4.2, rule 2). An
 * article can only be moved forward within this window: beyond it, it is
 * never a candidate, whatever the source monotony. Eight articles are about
 * three screens: enough to find another source in most real feeds, too few
 * for a move to read as a date anomaly. A wide window would mix better but
 * would end up raising old content above recent, which rule 2 forbids; a
 * window of 2 would only break immediate duplicates and let a prolific feed
 * occupy the screen in pairs.
 */
private const val LOOKAHEAD_WINDOW = 8

/**
 * Reorders [articles] to spread sources without betraying chronology.
 *
 * [articles] is expected in server order, i.e. reverse chronological; the
 * output is an exact permutation of the input.
 *
 * The process is greedy: at each position, take the oldest still-available
 * candidate whose feed differs from the previous one, looking only at the
 * first [LOOKAHEAD_WINDOW] remaining. When no eligible candidate exists in
 * the window (all nearby articles come from the same feed), the head is taken
 * as is: rule 1 yields to rule 2, per the priority order of SPECS.md §4.2
 * ("as long as another source is available").
 *
 * Recency guarantees of this choice:
 * - forward: an article is never presented more than `LOOKAHEAD_WINDOW - 1`
 *   positions before its chronological rank, since it is only a candidate
 *   once it enters the window;
 * - backward: a head article is deferred at most once in a row. Skipping it
 *   requires emitting an article from another feed, which makes the head
 *   eligible on the next turn, where it is the oldest candidate and thus
 *   chosen.
 *
 * The result depends only on [articles] and [previousTail]: no randomness, no
 * clock, no iteration over an unordered set (rule 3). The same set of
 * articles thus produces the same order on every display.
 *
 * @param previousTail tail of the previous page, so rule 1 also holds at the
 *   junction between two pages (rule 4). Only its last element constrains the
 *   result (monotony is only judged between immediate neighbours), but the
 *   caller naturally holds its page tail and need not know how many elements
 *   the rule consumes.
 */
fun interleaveBySource(
    articles: List<Article>,
    previousTail: List<Article> = emptyList(),
): List<Article> {
    val pending = articles.toMutableList()
    val ordered = ArrayList<Article>(articles.size)
    var previousFeedId = previousTail.lastOrNull()?.let { it.feed.id }
    while (pending.isNotEmpty()) {
        val chosen = pending.removeAt(nextIndex(pending, previousFeedId))
        previousFeedId = chosen.feed.id
        ordered += chosen
    }
    return ordered
}

/**
 * Index, in [pending], of the article to present next.
 *
 * Fallback to `0`: keeping server order is the least surprising behavior when
 * no alternative exists.
 */
private fun nextIndex(
    pending: List<Article>,
    previousFeedId: String?,
): Int {
    val window = minOf(LOOKAHEAD_WINDOW, pending.size)
    val eligible = (0 until window).firstOrNull { pending[it].feed.id != previousFeedId }
    return eligible ?: 0
}
