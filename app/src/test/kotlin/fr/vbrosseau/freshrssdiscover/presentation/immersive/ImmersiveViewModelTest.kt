package fr.vbrosseau.freshrssdiscover.presentation.immersive

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshness
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val NOW_SECONDS = 1_700_000_000L

/**
 * Continuous display duration the detector actually applies.
 *
 * Derived, never copied: these cases probe the threshold from both sides, and
 * a literal repeated in three files would keep asserting an old value after a
 * change to the default — passing while describing behaviour the application
 * no longer has.
 */
private val VISIBILITY_THRESHOLD_MILLIS = ReadingSettings.Default.continuousVisibilityMillis

/** A full-screen article is fully visible: the whole point of SPECS.md §4.8. */
private const val FULL_SCREEN = 1f

private const val ONE_HOUR_MILLIS = 60L * 60L * 1_000L
private const val SIX_HOURS_MILLIS = 6L * ONE_HOUR_MILLIS
private const val SEVEN_HOURS_MILLIS = 7L * ONE_HOUR_MILLIS

@OptIn(ExperimentalCoroutinesApi::class)
class ImmersiveViewModelTest {
    /** Kept as a field: the staleness cases advance its virtual scheduler. */
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_SECONDS * 1_000L)

    /**
     * Built lazily: the ViewModel loads its first page on creation, on
     * `Dispatchers.Main`. A property initializer would run before
     * [MainDispatcherRule] has substituted it.
     */
    private val viewModel: ImmersiveViewModel by lazy {
        ImmersiveViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    private val state get() = viewModel.uiState.value

    private val reportedBatches: List<Set<ArticleId>> get() = readSyncRepository.markCalls

    /** Holds an article full screen for the required duration, then reports again. */
    private fun watch(id: Long, millis: Long = VISIBILITY_THRESHOLD_MILLIS) {
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to FULL_SCREEN))
        clock.advanceBy(millis)
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to FULL_SCREEN))
    }

    // ----- First display ------------------------------------------------------

    @Test
    fun theFirstArticleOfTheFeedIsShown() {
        repository.enqueuePage(
            listOf(article(id = 1L, title = "Premier"), article(id = 2L, title = "Second")),
            nextCursor = PageCursor("c1"),
        )

        assertEquals("Premier", state.articles.first().title)
        assertEquals(DiscoverPhase.Idle, state.phase)
    }

    @Test
    fun nothingIsShownWhileTheFirstPageIsInFlight() {
        repository.pendingLoad = CompletableDeferred()

        assertEquals(DiscoverPhase.InitialLoading, state.phase)
        assertTrue(state.articles.isEmpty())
    }

    @Test
    fun theCachedArticlesAreShownBeforeTheNetworkAnswers() {
        // SPECS.md §5.1: full screen the defect would be even more visible
        // than in list mode, a single empty screen with nothing to swipe.
        repository.pendingLoad = CompletableDeferred()
        repository.cachedArticles.value = listOf(article(id = 9L, title = "Du cache"))

        assertEquals(listOf("Du cache"), state.articles.map { it.title })
    }

    @Test
    fun theFullScreenExcerptIsTheOneUsedByThisMode() {
        // List mode stops at 240 characters; full screen shows more
        // (SPECS.md §8, question 8).
        val summary = "mot ".repeat(500)
        repository.enqueuePage(listOf(article(id = 1L, summary = summary)))

        assertTrue(state.articles.single().excerpt.length > EXCERPT_LENGTH_OF_A_CARD)
    }

    @Test
    fun reloadingKeepsTheFullScreenExcerptLength() {
        // A divergence that actually existed: the refresh projected with the
        // List excerpt (240 characters), and a reloaded stack showed cards
        // truncated to the other mode's length. The projection is the same at
        // startup and on refresh.
        val summary = "mot ".repeat(500)
        repository.enqueuePage(listOf(article(id = 1L, summary = summary)))
        repository.enqueuePage(listOf(article(id = 2L, summary = summary)))

        viewModel.refresh()

        assertTrue(state.articles.single().excerpt.length > EXCERPT_LENGTH_OF_A_CARD)
    }

    // ----- Coming to the foreground (SPECS.md §5.1, like the List) -----------

    @Test
    fun aFirstShowingOnAFilledCacheAsksNothing() {
        // The quiet launch of the List, shared: switching from the List
        // creates this ViewModel, and a reload here would replace the feed
        // the List still displays (GOAL-042).
        repository.enqueuePage(listOf(article(id = 1L)))
        repository.enqueuePage(listOf(article(id = 2L)))
        viewModel.onScreenShown()

        assertEquals(0, repository.refreshCallCount)
        assertEquals(listOf(1L), state.articles.map { it.id })
    }

    // ----- Prefetch and end of feed -------------------------------------------

    @Test
    fun thePageAfterTheCurrentOneIsRequestedWithTheReturnedCursor() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("c2"))

        viewModel.loadMore()

        assertEquals(listOf(null, PageCursor("c1")), repository.requestedCursors)
        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
    }

    @Test
    fun aSecondRequestIsIgnoredWhileOneIsAlreadyInFlight() {
        // The swipe re-evaluates the threshold on every recomposition: without
        // a lock, several requests would leave before the first update.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        // Forces construction, hence the first page, before arming the
        // suspended load: otherwise initialization would be caught by it.
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingLoad = CompletableDeferred()

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(2, repository.loadCallCount)
    }

    @Test
    fun theEndOfTheFeedIsAnnouncedRatherThanSuffered() {
        // GOAL-012-T03: a swipe that stops responding is indistinguishable
        // from a failure.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
        assertEquals(2, state.pageCount, "un écran par article, plus celui qui dit la fin")
    }

    @Test
    fun aFinishedFeedIsNotAskedForMore() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.loadMore()

        assertEquals(1, repository.loadCallCount)
    }

    @Test
    fun anEmptyFeedIsSaidToBeEmpty() {
        repository.enqueuePage(emptyList(), nextCursor = null)

        assertTrue(state.articles.isEmpty())
        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
    }

    // ----- Errors -------------------------------------------------------------

    @Test
    fun aFailedPageKeepsWhatIsAlreadyDisplayed() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.NoNetwork)

        viewModel.loadMore()

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertEquals(DiscoverPhase.Failed(DiscoverFailure.NoNetwork), state.phase)
        assertTrue(state.isOffline)
    }

    @Test
    fun retryingAfterAFailureRequestsTheSamePageAgain() {
        repository.enqueueFailure(FeedError.ServerUnreachable)
        repository.enqueuePage(listOf(article(id = 1L)))

        viewModel.retry()

        assertEquals(listOf<PageCursor?>(null, null), repository.requestedCursors)
        assertEquals(listOf(1L), state.articles.map { it.id })
    }

    @Test
    fun anExpiredSessionEndsTheFeedWithoutAnyMessage() {
        repository.enqueueFailure(FeedError.SessionExpired)

        assertIs<DiscoverPhase.SessionEnded>(state.phase)
    }

    // ----- Marking as read ----------------------------------------------------

    @Test
    fun anArticleHeldOnScreenForTheRequiredDurationBecomesRead() {
        repository.enqueuePage(listOf(article(id = 1L)))

        watch(id = 1L)

        assertEquals(listOf(setOf(ArticleId(1L))), reportedBatches)
        assertTrue(state.articles.single().isRead)
    }

    @Test
    fun anArticleLeftBeforeTheRequiredDurationStaysUnread() {
        repository.enqueuePage(listOf(article(id = 1L)))

        watch(id = 1L, millis = VISIBILITY_THRESHOLD_MILLIS - 1L)

        assertTrue(reportedBatches.isEmpty())
        assertFalse(state.articles.single().isRead)
    }

    @Test
    fun comingBackToAReadArticleDoesNotMarkItAgain() {
        // GOAL-012-T04: going back neither unmarks nor re-reports; that would
        // be a request for nothing.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)))
        watch(id = 1L)

        watch(id = 2L)
        watch(id = 1L)

        assertEquals(listOf(setOf(ArticleId(1L)), setOf(ArticleId(2L))), reportedBatches)
    }

    @Test
    fun comingBackToAReadArticleLeavesItRead() {
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)))
        watch(id = 1L)

        watch(id = 2L)
        watch(id = 1L)

        assertTrue(state.articles.first { it.id == 1L }.isRead)
    }

    // ----- Automatic marking off (SPECS.md §4.5) ------------------------------

    @Test
    fun withAutomaticMarkingOffWatchingAnArticleLeavesItUnread() {
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)))

        watch(id = 1L)

        assertTrue(reportedBatches.isEmpty())
        assertFalse(state.articles.single().isRead)
    }

    @Test
    fun withAutomaticMarkingOffOpeningAnArticleStillMarksItRead() {
        // The trap of this setting: full screen, the only other way to mark is
        // opening (SPECS.md §4.7). Losing it would leave Immersive mode unable to
        // consume anything.
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)))

        assertTrue(viewModel.onArticleOpened(1L))

        assertEquals(listOf(setOf(ArticleId(1L))), reportedBatches)
        assertTrue(state.articles.single().isRead)
    }

    @Test
    fun turningAutomaticMarkingBackOnResumesWithoutARestart() {
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)))
        watch(id = 1L)

        settingsRepository.setAutomaticMarking(enabled = true)
        watch(id = 1L)

        assertEquals(listOf(setOf(ArticleId(1L))), reportedBatches)
    }

    // ----- Opening ------------------------------------------------------------

    @Test
    fun openingAnArticleMarksItReadWhateverItsPastVisibility() {
        repository.enqueuePage(listOf(article(id = 1L)))

        assertTrue(viewModel.onArticleOpened(1L))

        assertEquals(listOf(setOf(ArticleId(1L))), reportedBatches)
        assertTrue(state.articles.single().isRead)
    }

    @Test
    fun openingIsRefusedAndExplainedWhileOffline() {
        repository.enqueueFailure(FeedError.NoNetwork)
        repository.cachedArticles.value = listOf(article(id = 1L))
        viewModel.loadMore()

        assertFalse(viewModel.onArticleOpened(1L))

        assertTrue(state.isOfflineOpenNoticeVisible)
        assertTrue(reportedBatches.isEmpty())
    }

    @Test
    fun theRefusalNoticeIsDismissedOnDemand() {
        repository.enqueueFailure(FeedError.NoNetwork)
        repository.cachedArticles.value = listOf(article(id = 1L))
        viewModel.onArticleOpened(1L)

        viewModel.dismissOfflineOpenNotice()

        assertFalse(state.isOfflineOpenNoticeVisible)
    }

    private companion object {
        /** List-mode bound (`EXCERPT_MAX_LENGTH`), quoted for the comparison. */
        const val EXCERPT_LENGTH_OF_A_CARD = 240
    }
    // ----- Refresh (SPECS.md §4.6) --------------------------------------------

    @Test
    fun reloadingReplacesTheWholeStackRatherThanCompletingIt() {
        // Same rule as List mode: refresh replaces, it does not append.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 5L), article(id = 6L)), nextCursor = PageCursor("c9"))

        viewModel.refresh()

        assertEquals(listOf(5L, 6L), state.articles.map { it.id })
        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun reloadingRestartsThePaginationFromTheFreshPage() {
        // The old cursor would point to a place no longer in the stack.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("c9"))
        repository.enqueuePage(listOf(article(id = 3L)), nextCursor = null)

        viewModel.refresh()
        viewModel.loadMore()

        assertEquals(listOf(null, PageCursor("c9")), repository.requestedCursors)
        assertEquals(listOf(2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun theReloadIndicatorLastsExactlyAsLongAsTheRequest() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.pendingRefresh = CompletableDeferred()

        viewModel.refresh()
        assertTrue(state.isRefreshing)

        repository.completeRefresh(Outcome.Success(ArticlePage(listOf(article(id = 5L)), null)))
        assertFalse(state.isRefreshing)
        assertEquals(listOf(5L), state.articles.map { it.id })
    }

    @Test
    fun asecondReloadIsIgnoredWhileOneIsAlreadyInFlight() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.pendingRefresh = CompletableDeferred()

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun aFailedReloadKeepsTheStackAndSaysWhy() {
        // Nothing is discarded before having something to put in its place:
        // failing while emptying the screen would lose what was readable.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.NoNetwork)

        viewModel.refresh()

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertFalse(state.isRefreshing)
        assertIs<DiscoverPhase.Failed>(state.phase)
    }

    // ----- Feed staleness (SPECS.md §4.6) -------------------------------------

    @Test
    fun aFeedOlderThanSixHoursInvitesToRefresh() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(listOf(article(id = 1L)))

        assertTrue(state.showsStaleNotice)
    }

    @Test
    fun aRecentFeedInvitesToNothing() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = clock.nowEpochMillis()))
        repository.enqueuePage(listOf(article(id = 1L)))

        assertFalse(state.showsStaleNotice)
    }

    @Test
    fun offlineTheOfflineBannerSpeaksAloneAboutAnOldFeed() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.cachedArticles.value = listOf(article(id = 1L))
        repository.enqueueFailure(FeedError.NoNetwork)
        viewModel.loadMore()

        assertTrue(state.isStaleNoticeAvailable)
        assertFalse(state.showsStaleNotice)
    }

    @Test
    fun theInvitationIsSilencedByHand() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(listOf(article(id = 1L)))

        viewModel.dismissStaleNotice()

        assertFalse(state.showsStaleNotice)
        assertEquals(1, freshnessRepository.acknowledgeCallCount)
    }

    @Test
    fun anInvitationSilencedInTheOtherModeStaysSilentHere() {
        // The repository is shared: it carries the acknowledgement, not the
        // ViewModel, which the mode switch destroys and rebuilds.
        freshnessRepository.set(
            FeedFreshness(
                lastRefreshEpochMillis = staleSince(),
                acknowledgedRefreshEpochMillis = staleSince(),
            ),
        )
        repository.enqueuePage(listOf(article(id = 1L)))

        assertFalse(state.showsStaleNotice)
    }

    @Test
    fun theFeedGrowsOldWithoutAnyEventAtAll() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = clock.nowEpochMillis()))
        repository.enqueuePage(listOf(article(id = 1L)))
        assertFalse(state.showsStaleNotice)

        clock.advanceBy(SIX_HOURS_MILLIS)
        dispatcher.scheduler.advanceTimeBy(SIX_HOURS_MILLIS)

        assertTrue(state.showsStaleNotice)
    }

    /** A server-contact timestamp old enough for the notice to be due. */
    private fun staleSince(): Long = clock.nowEpochMillis() - SEVEN_HOURS_MILLIS

    // ----- Quiet launch (SPECS.md §5.1, GOAL-015) -----------------------------

    @Test
    fun aGarnishedCacheLaunchesWithoutAnyNetworkRequest() {
        repository.cachedArticles.value = listOf(article(id = 1L))

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertEquals(DiscoverPhase.Idle, state.phase)
        assertEquals(0, repository.loadCallCount)
    }

    @Test
    fun anEmptyCacheTriggersTheOnlyAutomaticLoad() {
        repository.enqueuePage(listOf(article(id = 1L)))

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertEquals(1, repository.loadCallCount)
    }
}
