package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.toUiModel

/**
 * State displayed by both feed modes (SPECS.md §4.8).
 *
 * A single state serves both List and Immersive: SPECS.md §4.8 states that content
 * and loading rules are identical, only the presentation differs. Two twin
 * states with copied transitions previously diverged, exactly as
 * ARCHITECTURE.md §9.6 predicts. Mode-specific data (excerpt length, the
 * pager's end page) goes through the projection and derivations, never through
 * a second state.
 *
 * Articles and loading state are separate: SPECS.md §4.4 requires that a
 * failed next-page load does not clear what is already displayed, which a
 * sealed type carrying the list only in its "loaded" case could not satisfy.
 */
data class FeedUiState(
    val articles: List<ArticleUiModel> = emptyList(),
    val phase: DiscoverPhase = DiscoverPhase.InitialLoading,
    /**
     * User-requested refresh (SPECS.md §4.6): pull in List mode, button in
     * Immersive mode.
     *
     * Kept outside [phase] deliberately: a refresh happens on top of a feed
     * that already has a state (idle, ended, failed), and folding it into the
     * phase would require remembering which phase to restore.
     */
    val isRefreshing: Boolean = false,
    /**
     * The last request failed for lack of network (SPECS.md §5.2).
     *
     * Distinct from `DiscoverPhase.Failed(NoNetwork)`, which reports that a
     * *load* failed: this flag describes the regime the app is in, and it is
     * what drives the offline banner.
     */
    val isOffline: Boolean = false,
    /**
     * An article open was refused for lack of network (SPECS.md §5.2).
     *
     * A boolean rather than a message type: it is the only transient notice on
     * these screens, and an abstraction should wait for its second use case
     * (AGENTS.md §2).
     */
    val isOfflineOpenNoticeVisible: Boolean = false,
    /**
     * The server has been unreachable long enough to say so (SPECS.md §4.6).
     * Decided by the domain; this field reports the verdict.
     */
    val isStaleNoticeAvailable: Boolean = false,
) {
    /**
     * The offline banner only shows on top of readable content: with no
     * articles, the lack of network is not a degraded regime but the only
     * thing to say, and the full-screen message explains it instead.
     */
    val showsOfflineBanner: Boolean
        get() = isOffline && articles.isNotEmpty()

    /**
     * Whether the refresh invitation is shown. Suppressed when a message would
     * be wrong: offline, the banner already explains why the feed is stale;
     * while refreshing, the request is already in flight; with no articles,
     * there is no stale feed but an empty screen with its own message.
     */
    val showsStaleNotice: Boolean
        get() = isStaleNoticeAvailable && !isOffline && !isRefreshing && articles.isNotEmpty()
}

/**
 * Replaces the list with the delivered page and restarts from the top
 * (SPECS.md §4.6).
 *
 * Nothing is reset on the ViewModel side: the read detector's
 * `onVisibilityChanged` already discards timers of absent articles, and
 * articles already reported to the server must not be reported again (that
 * would issue a useless request).
 *
 * The phase follows the delivered page: `Idle` if a cursor remains,
 * `EndOfFeed` otherwise. This also clears a previous failure (the network
 * just responded) and reopens a feed that had ended if the server has new
 * content.
 */
internal fun FeedUiState.refreshedWith(
    page: ArticlePage,
    nowEpochMillis: Long,
): FeedUiState = copy(
    articles = page.articles.map { article -> article.toUiModel(nowEpochMillis) },
    phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
    isOffline = false,
)

/**
 * Appends articles missing from the list without touching those already in it.
 *
 * Displayed articles are never reordered: rule 3 of SPECS.md §4.2 requires a
 * given set of articles to always appear in the same order. In Immersive mode,
 * reordering under the finger would change which article the next gesture
 * reveals.
 *
 * @param atHead true to insert unknown articles at the head, as the server's
 *   first page does on top of the already displayed cache.
 */
internal fun FeedUiState.merging(
    articles: List<Article>,
    nowEpochMillis: Long,
    atHead: Boolean,
): FeedUiState {
    val known = this.articles.mapTo(mutableSetOf(), ArticleUiModel::id)
    val fresh = articles.filterNot { it.id.value in known }.map { it.toUiModel(nowEpochMillis) }
    if (fresh.isEmpty()) return this

    return copy(articles = if (atHead) fresh + this.articles else this.articles + fresh)
}

/**
 * Flips the read flag without moving the article: removing it would shift the
 * reading position. The flag never returns to false (GOAL-012-T04); there is
 * no inverse transition.
 */
internal fun FeedUiState.markingRead(ids: Set<ArticleId>): FeedUiState =
    copy(articles = articles.map { if (ArticleId(it.id) in ids) it.copy(isRead = true) else it })

/**
 * Already loaded articles are kept (SPECS.md §4.4): clearing them because the
 * next page failed would punish the user for nearing the bottom of the feed.
 * Offline, they are most of what remains.
 */
internal fun FeedUiState.failedWith(error: FeedError): FeedUiState = copy(
    phase = when (error) {
        FeedError.SessionExpired -> DiscoverPhase.SessionEnded
        FeedError.NoNetwork -> DiscoverPhase.Failed(DiscoverFailure.NoNetwork)
        FeedError.ServerUnreachable -> DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable)
        is FeedError.Unexpected -> DiscoverPhase.Failed(DiscoverFailure.Unexpected)
    },
    isOffline = error == FeedError.NoNetwork,
)

/**
 * Cache populated at launch: nothing to request, the feed is the one left
 * behind (SPECS.md §5.1). The phase moves to idle; otherwise the screen would
 * announce a load that does not exist.
 */
internal fun FeedUiState.settledFromCache(): FeedUiState =
    if (phase == DiscoverPhase.InitialLoading) copy(phase = DiscoverPhase.Idle) else this
