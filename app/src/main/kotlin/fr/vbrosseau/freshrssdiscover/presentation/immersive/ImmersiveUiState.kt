package fr.vbrosseau.freshrssdiscover.presentation.immersive

import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedUiState

/**
 * State displayed by the Immersive view: the same state as List mode, under this
 * mode's historical name.
 *
 * An alias rather than a class: two twin states existed here and diverged
 * (the reload projected excerpts at the List length). See [FeedUiState] for
 * the state itself. What Immersive keeps of its own lives in its projection
 * (`ArticleUiModel.immersiveExcerpt`) and in [pageCount].
 *
 * The reload of SPECS.md §4.6 is included, but not its gesture: a vertical
 * pull on a pager that already snaps vertically would be two gestures on one
 * axis. A button triggers it here, and the state carried is the same:
 * `isRefreshing`.
 */
typealias ImmersiveUiState = FeedUiState

/**
 * Number of screens the pager traverses: the articles, plus one.
 *
 * The extra page is the Immersive equivalent of List mode's footer: where the end
 * of feed is stated, loading is shown, and failure offers a retry. Without it,
 * flicking would simply stop responding after the last article, which is
 * indistinguishable from a failure (SPECS.md §4.4).
 *
 * Declared here rather than in the shared state: List has no pager, and
 * carrying this count in the common state would be wrong on that side.
 */
val ImmersiveUiState.pageCount: Int get() = articles.size + 1
