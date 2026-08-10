package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedUiState

/**
 * State displayed by the Swipe view: the same state as List mode, under this
 * mode's historical name.
 *
 * An alias rather than a class: two twin states existed here and diverged
 * (the reload projected excerpts at the List length). See [FeedUiState] for
 * the state itself. What Swipe keeps of its own lives in its projection
 * (`toSwipeUiModel`) and in [pageCount].
 *
 * The reload of SPECS.md §4.6 is included, but not its gesture: pull-to-refresh
 * is a vertical motion on a list, and full screen there is no list to pull.
 * A button triggers it here, and the state carried is the same: `isRefreshing`.
 */
typealias SwipeUiState = FeedUiState

/**
 * Number of screens the pager traverses: the articles, plus one.
 *
 * The extra page is the Swipe equivalent of List mode's footer: where the end
 * of feed is stated, loading is shown, and failure offers a retry. Without it,
 * swiping would simply stop responding after the last article, which is
 * indistinguishable from a failure (SPECS.md §4.4).
 *
 * Declared here rather than in the shared state: List has no pager, and
 * carrying this count in the common state would be wrong on that side.
 */
val SwipeUiState.pageCount: Int get() = articles.size + 1
