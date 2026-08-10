package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.CACHED_FEED_LIMIT
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.read.ReadDetector
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One-shot facts the screen surfaces as a toast.
 *
 * Distinct from [FeedUiState]: a toast is fired, not displayed-until-
 * acknowledged, so modelling it as state would either replay it on every
 * recomposition or force an acknowledgement gesture the message does not
 * deserve. The repository's hand-acknowledged notices keep their doctrine;
 * this channel only carries what must merely be *noticed*.
 */
enum class FeedEvent {
    /** A load or reload failed because the API did not answer. */
    ServerUnreachable,
}

/**
 * The feed engine, shared by List and swipe modes (SPECS.md §4.8).
 *
 * SPECS.md §4.8 says both modes carry the same content and the same reading
 * and loading rules; only the presentation changes. Pagination, refresh,
 * cache bootstrap, marking, and notices live once here, and each mode only
 * provides its projection ([project]), that is, its excerpt length.
 *
 * Two twin ViewModels previously existed and diverged twice (ARCHITECTURE.md
 * §9.6): the swipe mode's `loadMore` without the `isRefreshing` guard (fixed
 * in GOAL-028), then a swipe refresh projecting excerpts at the List length.
 * Each fix had to be ported twice, with two test suites.
 *
 * A base class rather than a delegated object: the screen's eight public
 * gestures are the engine itself, and method-by-method delegation would only
 * copy them into each mode.
 */
abstract class FeedSessionViewModel(
    private val articleRepository: ArticleRepository,
    private val readSyncRepository: ReadSyncRepository,
    settingsRepository: SettingsRepository,
    freshnessRepository: FeedFreshnessRepository,
    private val clock: Clock,
    /** Projection from a domain article to its card; the only point distinguishing the modes. */
    private val project: ArticleProjection,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    /**
     * Buffered so an event emitted while nobody collects — a failure landing
     * during a configuration change — is delivered to the next collector
     * instead of being dropped.
     */
    private val _events = Channel<FeedEvent>(Channel.BUFFERED)
    val events: Flow<FeedEvent> = _events.receiveAsFlow()

    /**
     * Watches feed staleness (SPECS.md §4.6).
     *
     * Built here rather than injected: it needs [viewModelScope], so it must
     * live exactly as long as this screen. What it observes is shared, hence
     * the acknowledgement common to both modes.
     */
    private val staleness = FeedStalenessWatcher(freshnessRepository, clock, viewModelScope)

    /**
     * Position reached in the feed. `null` requests the start, and only
     * `null`: fabricating an empty cursor would silently re-request the
     * first page.
     */
    private var cursor: PageCursor? = null

    /**
     * Lock for an in-flight load.
     *
     * A property, not the published state: scrolling requests the next page
     * on every frame, and waiting for `LoadingMore` to be visible in the
     * `StateFlow` would let several requests through before the first
     * update. The ViewModel is only touched from the main dispatcher, so a
     * boolean suffices; there is no concurrency to arbitrate.
     */
    private var isLoading: Boolean = false

    /**
     * The in-flight page load, so the refresh can cancel it (GOAL-028,
     * replaced in GOAL-029).
     *
     * Without it, a page requested before the pull and returned after it was
     * appended under the refreshed list and overwrote the refresh cursor:
     * pagination silently resumed the abandoned traversal. Since GOAL-027,
     * its cache write also reinserted rows that `retainOnly` had just
     * removed.
     *
     * Cancellation rather than the generation counter of GOAL-028: the
     * counter only discarded the display on arrival; the request ran to
     * completion and its `cache.save()` still executed if the page landed
     * after the refresh's `retainOnly`. Cancellation propagates to Ktor and
     * the repository: the request is abandoned, not just its result, and the
     * cache write with it. It is not a wait on [isLoading]; the gesture
     * never waits behind a slow request. The in-flight page is lost even if
     * the refresh then fails: the gesture disowned the old traversal, and
     * retry is the way back.
     */
    private var loadJob: Job? = null

    /**
     * Tail of the last rendered page. Its last article suffices, monotonicity
     * being judged only between immediate neighbors (SPECS.md §4.2, rule 4).
     *
     * It lives here, with the [cursor]: it is the same pagination-continuity
     * state, and housing it in the repository, a singleton shared by both
     * modes, let one mode's junction be contaminated by the other's
     * pagination.
     */
    private var paginationTail: List<Article> = emptyList()

    /**
     * True once a server page has been merged into the list.
     *
     * This is what closes the cache's door. The cache flow re-emits on every
     * write, so after every received page: continuing to consume it would
     * make articles reappear, as pagination proceeds, in an order the server
     * did not dictate, in the middle of a list being browsed. The cache
     * bootstraps the display (SPECS.md §5.1), the server continues it; never
     * both at once.
     */
    private var hasServerContent: Boolean = false

    /**
     * Decides read status from visibility observations.
     *
     * Built here rather than injected: its state, the running timers and the
     * articles already reported, only makes sense for this reading session.
     * A Hilt-shared instance would outlive the screen and report articles
     * from a previous session as read. It is also what makes going back
     * harmless in swipe mode (GOAL-012-T04): an already-reported article is
     * never reported twice.
     *
     * Rebuilt on every settings change, which resets its timers. Deliberate:
     * a threshold modified mid-reading must not apply to a timer started
     * under the old one. Already-reported articles are forgotten at the same
     * time, without consequence: they are already `isRead` locally, and
     * [markRead] filters them before transmission.
     *
     * `null` when automatic marking is off (SPECS.md §4.5): the absence of a
     * detector says better than a side boolean that there is nothing to
     * feed; consulting it cannot be forgotten.
     */
    private var readDetector: ReadDetector? = ReadDetector(clock)

    /** Articles already sent to the sync repository, over the screen's lifetime. */
    private val alreadyReported = mutableSetOf<ArticleId>()

    /**
     * True once the bootstrap decision has been made: it is made only once.
     *
     * SPECS.md §5.1: launch shows the cache and stops there. The only
     * exception is an empty cache (first open, return after sign-out), where
     * requesting nothing would leave a dead app. The decision is made on the
     * cache's first emission, never after: a later write that empties the
     * list must not trigger a request behind the user's back.
     */
    private var hasDecidedBootstrap = false

    init {
        /*
         * Thresholds come from settings, not compiled constants: a change
         * applies without a restart, which is why the repository exposes a
         * flow rather than a one-shot read (SPECS.md §6).
         *
         * The automatic-marking switch takes the same path: opening a second
         * flow would have a single rule observe two sources, and the
         * switch-off would not necessarily arrive at the same time as the
         * threshold.
         */
        settingsRepository.observeReadingSettings()
            .onEach { settings ->
                readDetector = if (settings.autoMarkAsReadEnabled) {
                    ReadDetector(
                        clock = clock,
                        visibleFractionThreshold = settings.visibleFraction,
                        continuousVisibilityMillis = settings.continuousVisibilityMillis,
                    )
                } else {
                    null
                }
            }
            .launchIn(viewModelScope)

        /*
         * Replay at startup: what could not be sent in the previous session,
         * notably the app closed offline, goes out here. Otherwise a marking
         * would wait for the next reading to be transmitted (SPECS.md §4.5).
         */
        viewModelScope.launch { readSyncRepository.flush() }

        /*
         * Subscribed before the first `loadPage()`, and the order is the
         * whole point: SPECS.md §5.1 wants the feed to show its content
         * without waiting for the network. Subscribing after would let the
         * load finish first, and an empty screen during a request looks like
         * an app with no content when it has some.
         */
        articleRepository.observeCachedArticles(CACHED_FEED_LIMIT)
            .onEach { cached ->
                /*
                 * The cache bootstraps the list, it never replaces it.
                 * Reapplying the whole emission would be the flaw to avoid:
                 * the flow re-emits on every write, and the cache's order is
                 * not that of the accumulated pages; the ongoing reading
                 * would jump. Only absent articles are appended, at the end,
                 * without reordering or removing anything. Past the first
                 * server page, nothing more is added (see [hasServerContent]).
                 */
                val now = clock.nowEpochMillis()
                if (!hasServerContent) {
                    _uiState.update { it.merging(cached, now, atHead = false, project = project) }
                }
                if (!hasDecidedBootstrap) {
                    hasDecidedBootstrap = true
                    if (cached.isEmpty()) load() else _uiState.update { it.settledFromCache() }
                }
            }
            .launchIn(viewModelScope)

        staleness.isStale
            .onEach { isStale -> _uiState.update { it.copy(isStaleNoticeAvailable = isStale) } }
            .launchIn(viewModelScope)
    }

    /**
     * The screen comes to the foreground (SPECS.md §5.1, GOAL-025).
     *
     * A screen without articles queries the server. SPECS.md §5.1 says no
     * request leaves while there is something to show; its converse was only
     * applied once, at the cache's first sample ([hasDecidedBootstrap]).
     * Reading everything and coming back thus left an empty, silent screen,
     * escapable only via the title-bar button.
     *
     * Hooked to coming to the foreground, never to the empty state. A server
     * with nothing to return leaves the screen empty: reacting to that state
     * would re-request endlessly. Here, arriving on the feed, returning from
     * settings, or waking from sleep each count as one attempt.
     *
     * [refresh] rather than [load]: an end-of-feed cursor leads nowhere, and
     * the refresh is the only path that transmits pending markings before
     * querying (GOAL-024), which is exactly the situation, since the screen
     * is empty because everything was just read.
     *
     * Each excluded phase has its reason: an initial load is already in
     * flight (the empty-cache startup case, where doubling the request would
     * be the easiest mistake here), a failure already carries its retry and
     * relaunching on every return would hammer an absent network, and a
     * terminated session is about to leave the screen.
     */
    fun onScreenShown() {
        val state = _uiState.value
        if (state.articles.isNotEmpty() || state.isRefreshing) return
        if (state.phase != DiscoverPhase.Idle && state.phase != DiscoverPhase.EndOfFeed) return

        refresh()
    }

    /**
     * Requests the next page.
     *
     * Idempotent and inert outside the useful case: end of feed, a load in
     * progress, a refresh, an unacknowledged error, and a terminated session
     * all ignore it. This lets the screen call it freely, on scroll or
     * swipe, without knowing the current state. The `isRefreshing` guard is
     * the one swipe mode had lost (GOAL-028): without it, a page could start
     * during the refresh with the old traversal's cursor.
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.phase != DiscoverPhase.Idle || state.isRefreshing) return
        load()
    }

    /**
     * Retries after a failure.
     *
     * Distinct from [loadMore]: resuming after an error is a user gesture,
     * and conflating it with prefetching would loop the request as long as
     * the network is absent.
     */
    fun retry() {
        if (_uiState.value.phase !is DiscoverPhase.Failed) return
        load()
    }

    /**
     * Reloads the feed from the start (SPECS.md §4.6): pull in List mode,
     * button in swipe mode.
     *
     * The gesture clears the display and restarts from the top: the list is
     * replaced, not extended, and the screen returns to the top. What the
     * user was looking at disappears; the price of a gesture with an
     * immediate, readable effect, accepted by the specification.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        // The gesture disowns the ongoing traversal: the still-in-flight page
        // is cancelled, request, result, and cache write with it (GOAL-028,
        // then GOAL-029 for the scope).
        loadJob?.cancel()
        loadJob = null
        isLoading = false
        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            /*
             * Tell the server what has been read before asking it for more.
             * Otherwise the refresh queries a server that does not yet know
             * about the last seconds' readings: marking batching holds up to
             * 5 s (SPECS.md §8, question 4). The server then returns those
             * articles as unread, they retake their place in the 40-item
             * page, and the new ones do not appear; a second refresh is
             * needed.
             *
             * This is the only place in the code where `flush` is awaited
             * before anything else: elsewhere it is fire-and-forget, marking
             * being optimistic. Here its result conditions the correctness
             * of what the server will return. A failure blocks nothing: the
             * queue retains, and the refresh proceeds anyway.
             */
            readSyncRepository.flush()

            when (val result = articleRepository.refresh()) {
                is Outcome.Success -> {
                    cursor = result.value.nextCursor
                    // The tail follows the traversal the cursor follows: the
                    // next page extends the refreshed page, and its junction
                    // is judged against it.
                    paginationTail = listOfNotNull(result.value.articles.lastOrNull())
                    hasServerContent = true
                    _uiState.update { it.refreshedWith(result.value, clock.nowEpochMillis(), project) }
                }

                is Outcome.Failure -> {
                    _uiState.update { it.failedWith(result.error) }
                    result.error.toFeedEvent()?.let(_events::trySend)
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Processes a visibility observation.
     *
     * Called periodically by the screen, including when nothing moves: the
     * rule in SPECS.md §4.5 concerns a continuous duration, and the detector
     * being pure, that duration only elapses between observations. In swipe
     * mode this is the main integration trap: a still, full-screen article
     * produces no event, and without a second observation it would never be
     * reported.
     *
     * No effect when automatic marking is off: there is then no detector to
     * feed. Opening an article still marks it read (SPECS.md §4.7); see
     * [onArticleOpened].
     */
    fun onVisibilityChanged(visibility: Map<ArticleId, Float>) {
        val justRead = readDetector?.onVisibilityChanged(visibility).orEmpty()
        if (justRead.isEmpty()) return

        markRead(justRead)
    }

    /**
     * Opens an article: marks it read and says whether the open can proceed.
     *
     * Read regardless of past visibility (SPECS.md §4.7): touching an
     * article is a clearer act of reading than any display duration, and
     * waiting for the double threshold would bring back into the feed the
     * article the user just opened.
     *
     * Even with automatic marking off: the switch in SPECS.md §4.5 only
     * stops visibility detection. Extending it to opening would conflate a
     * deliberate gesture with a side effect of scrolling.
     *
     * False offline (SPECS.md §5.2): the Custom Tab would only show the
     * browser's error page without saying why, and the article would pass
     * for read without having been readable. An explicit notice is published
     * instead, and nothing is marked.
     *
     * @param articleId identifier in its raw form, the one the list uses as
     *   a key.
     * @return true if the caller should open the link.
     */
    fun onArticleOpened(articleId: Long): Boolean {
        if (_uiState.value.isOffline) {
            _uiState.update { it.copy(isOfflineOpenNoticeVisible = true) }
            return false
        }

        markRead(setOf(ArticleId(articleId)))
        return true
    }

    /** Dismisses the blocked-open notice: it has been read, it disappears. */
    fun dismissOfflineOpenNotice() {
        _uiState.update { it.copy(isOfflineOpenNoticeVisible = false) }
    }

    /**
     * Silences the refresh invitation without refreshing.
     *
     * The acknowledgement goes to the shared repository: it applies to both
     * modes at once and will expire on its own at the next server contact.
     */
    fun dismissStaleNotice() {
        staleness.acknowledge()
    }

    /**
     * Marks locally, then transmits what has not already been transmitted.
     *
     * The local state switches to read immediately, without waiting for the
     * server (optimistic marking, SPECS.md §4.5), and the article stays in
     * place, only its flag changes: removing it would shift content under
     * the finger.
     *
     * The transmission filter covers the detector's reconstruction on a
     * settings change: it then forgets already-reported articles and would
     * re-report them at the next sample. Remote marking is idempotent, but
     * the request would be useless.
     *
     * `flush` immediately follows `markAsRead`: marking being optimistic,
     * local state is already up to date and a transmission failure is not
     * visible; the queue keeps it for later.
     */
    private fun markRead(ids: Set<ArticleId>) {
        _uiState.update { it.markingRead(ids) }

        val unreported = ids - alreadyReported
        if (unreported.isEmpty()) return

        alreadyReported += unreported
        viewModelScope.launch {
            readSyncRepository.markAsRead(unreported)
        }
    }

    private fun load() {
        if (isLoading) return
        isLoading = true

        val isFirstPage = cursor == null
        _uiState.update {
            it.copy(phase = if (isFirstPage) DiscoverPhase.InitialLoading else DiscoverPhase.LoadingMore)
        }

        // Kept so the refresh can cancel it: a cancelled page never comes
        // back, neither state, nor cursor, nor cache write.
        loadJob = viewModelScope.launch {
            try {
                val result = articleRepository.loadPage(cursor, paginationTail)
                // Released before processing: a page already entirely
                // displayed chains onto the next one, and a still-held lock
                // would make that chained call a no-op.
                isLoading = false

                when (result) {
                    is Outcome.Success -> onPageLoaded(result.value)
                    is Outcome.Failure -> {
                        _uiState.update { it.failedWith(result.error) }
                        result.error.toFeedEvent()?.let(_events::trySend)
                    }
                }
            } finally {
                // Covers cancellation: without this release, the lock would
                // stay held and no page would ever leave after a refresh.
                isLoading = false
            }
        }
    }

    private fun onPageLoaded(page: ArticlePage) {
        val shownBefore = _uiState.value.articles.size
        val now = clock.nowEpochMillis()

        _uiState.update { state ->
            /*
             * The very first page arrives on top of the already-displayed
             * cache: they are the same articles plus a few new ones, and
             * putting those at the bottom would show them far from their
             * date. It therefore merges like a refresh, at the head, without
             * reordering. Subsequent pages extend the feed: their place is
             * at the end.
             */
            state.merging(page.articles, now, atHead = !hasServerContent, project = project)
                .copy(
                    phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
                    isOffline = false,
                )
        }

        hasServerContent = true
        cursor = page.nextCursor
        // The last article as rendered: the page arrives already merged, and
        // that order is what the next junction must extend.
        paginationTail = listOfNotNull(page.articles.lastOrNull())

        /*
         * A page whose content was already entirely displayed (the ordinary
         * launch case, the cache having outrun the network by several pages)
         * grows nothing. Stopping there would deliver a list that stops
         * growing silently, indistinguishable from a failure (SPECS.md
         * §4.4): chain onto the next page, the only one that can bring
         * anything new. The chain is finite, the cursor advancing each round
         * until the end of the feed.
         */
        if (_uiState.value.articles.size == shownBefore && page.hasMore) load()
    }
}

/**
 * Which failures deserve a toast on top of the failure block, decided once
 * for the load and reload paths.
 *
 * Only the unreachable server (GOAL-030): `NoNetwork` already has the offline
 * banner as its regime (SPECS.md §5.2), and a toast on every offline scroll
 * would nag about a state the screen is already stating. The remaining causes
 * have nothing more to say than the block already does.
 */
private fun FeedError.toFeedEvent(): FeedEvent? =
    if (this == FeedError.ServerUnreachable) FeedEvent.ServerUnreachable else null
