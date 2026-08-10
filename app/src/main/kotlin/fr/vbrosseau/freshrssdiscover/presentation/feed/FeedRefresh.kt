package fr.vbrosseau.freshrssdiscover.presentation.feed

/**
 * The refresh action as a destination publishes it to the title bar.
 *
 * This type exists because the button and the feed do not live in the same
 * place: the title bar belongs to the app scaffold, above the navigation
 * graph, while the action belongs to the displayed destination's ViewModel.
 * One of them has to cross the boundary, and it is the action; moving the
 * bar into each screen would force each to redraw a title and a navigation
 * bar.
 *
 * `null` means "this destination has nothing to refresh", not "nothing to do
 * right now": that is what leaves the bar bare on the settings screen
 * without it having to ask.
 */
data class FeedRefresh(
    val isRefreshing: Boolean,
    val onRefresh: () -> Unit,
)
