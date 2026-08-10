package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedUiState

/**
 * What the List screen displays: the state shared by both modes, under the
 * name this screen has always used.
 *
 * An alias rather than a class: both modes carry the same content and the
 * same rules (SPECS.md §4.8). See [FeedUiState], which documents the state
 * itself.
 */
typealias DiscoverUiState = FeedUiState

/**
 * Where the feed stands.
 *
 * The outcomes of a load (ongoing, working, finished, failed) are distinct
 * cases rather than crossed booleans: SPECS.md §4.4 requires that a list
 * which stops growing is never confused with a failure, and two independent
 * flags would allow exactly the ambiguous "neither loading, nor done, nor in
 * error" state.
 */
sealed interface DiscoverPhase {
    /** First page in flight: nothing is displayable yet. */
    data object InitialLoading : DiscoverPhase

    /** Articles are displayed and a page remains to be requested. */
    data object Idle : DiscoverPhase

    /** Next page in flight, below the articles already displayed. */
    data object LoadingMore : DiscoverPhase

    /** No articles left: the feed ends with an explicit message. */
    data object EndOfFeed : DiscoverPhase

    /** A load failed; [failure] carries the message and the retry action. */
    data class Failed(val failure: DiscoverFailure) : DiscoverPhase

    /**
     * The server rejected the token.
     *
     * Not a read error but the end of the session: the repository pairs it
     * with an invalidation and the root switch returns to the login screen on
     * its own (SPECS.md §3.4). The screen therefore displays nothing special,
     * being about to disappear, but it stops requesting pages; otherwise
     * scrolling would request one on every frame until the switch.
     */
    data object SessionEnded : DiscoverPhase
}

/**
 * What prevented the load, reduced to what is told to the user.
 *
 * `FeedError.SessionExpired` is absent: it calls for no message, and
 * including it would force the screen to handle a case it must not display.
 * `FeedError.Unexpected` loses its technical message here: it goes to the
 * logs, never to the display.
 */
enum class DiscoverFailure {
    NoNetwork,
    ServerUnreachable,
    Unexpected,
}
