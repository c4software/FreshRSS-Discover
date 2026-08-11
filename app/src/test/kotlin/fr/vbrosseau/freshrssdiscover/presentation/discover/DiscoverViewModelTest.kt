package fr.vbrosseau.freshrssdiscover.presentation.discover

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

private const val ONE_HOUR_MILLIS = 60L * 60L * 1_000L
private const val SIX_HOURS_MILLIS = 6L * ONE_HOUR_MILLIS
private const val SEVEN_HOURS_MILLIS = 7L * ONE_HOUR_MILLIS
private const val TWELVE_HOURS_MILLIS = 12L * ONE_HOUR_MILLIS

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    /**
     * Kept at hand: the staleness cases advance its virtual scheduler,
     * otherwise the periodic wake-up would really wait.
     */
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val repository = FakeArticleRepository()
    private val clock = FakeClock(NOW_SECONDS * 1_000L)

    /**
     * Built lazily: the ViewModel loads its first page on creation, on
     * `Dispatchers.Main`. A property initializer would run before
     * [MainDispatcherRule] has substituted it.
     */
    private val viewModel: DiscoverViewModel by lazy {
        DiscoverViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    private val freshnessRepository = FakeFeedFreshnessRepository()

    /** Receives the mark batches. */
    private val readSyncRepository = FakeReadSyncRepository()

    private val settingsRepository = FakeSettingsRepository()

    private val state get() = viewModel.uiState.value

    /** Batches sent to the sync repository, in emission order; batching itself is verified. */
    private val reportedBatches: List<Set<ArticleId>> get() = readSyncRepository.markCalls

    private val readArticles: Set<ArticleId> get() = reportedBatches.flatten().toSet()

    // ----- First load ---------------------------------------------------------

    @Test
    fun theFirstPageIsRequestedWithoutAnyCursor() {
        // Only `null` requests the start of the feed: an empty cursor would
        // re-request the first page without anything signalling it.
        repository.enqueuePage(listOf(article(id = 1L)))

        viewModel.loadMore()

        assertEquals(listOf<PageCursor?>(null), repository.requestedCursors)
        assertEquals(1, repository.loadCallCount)
    }

    @Test
    fun nothingIsShownWhileTheFirstPageIsInFlight() {
        repository.pendingLoad = CompletableDeferred()

        assertEquals(DiscoverPhase.InitialLoading, state.phase)
        assertTrue(state.articles.isEmpty())
    }

    @Test
    fun theArticlesOfTheFirstPageAreShown() {
        repository.enqueuePage(
            listOf(article(id = 1L, title = "Premier"), article(id = 2L, title = "Second")),
            nextCursor = PageCursor("c1"),
        )

        assertEquals(listOf("Premier", "Second"), state.articles.map { it.title })
        assertEquals(DiscoverPhase.Idle, state.phase)
    }

    @Test
    fun theRelativeDateComesFromTheInjectedClock() {
        // Never `System.currentTimeMillis()`: without an injected clock, the
        // displayed date would depend on the machine time.
        repository.enqueuePage(listOf(article(id = 1L, publishedAtEpochSeconds = NOW_SECONDS - 7_200L)))

        assertEquals(RelativeTime.Hours(2), state.articles.single().publishedAt)
    }

    // ----- Pagination ---------------------------------------------------------

    @Test
    fun articlesAccumulatePageAfterPage() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("c2"))

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
    }

    @Test
    fun theCursorOfTheLastPageIsWhatAsksForTheNext() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("c2"))

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(listOf(null, PageCursor("c1"), PageCursor("c2")), repository.requestedCursors)
    }

    @Test
    fun twoLoadMoreCallsInFlightTriggerASingleRequest() {
        // Scrolling calls `loadMore()` on every frame: without idempotence,
        // the same page would be requested several times.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        // Forces ViewModel construction: its first page must complete before a
        // suspended load is armed.
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingLoad = CompletableDeferred()

        viewModel.loadMore()
        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(2, repository.loadCallCount)
        assertEquals(DiscoverPhase.LoadingMore, state.phase)

        repository.completeLoad(Outcome.Success(ArticlePage(listOf(article(id = 2L)), null)))
        assertEquals(2, state.articles.size)
    }

    @Test
    fun theArticlesAlreadyShownStayVisibleWhileTheNextPageLoads() {
        // An indicator replacing the list would lose the reading in progress.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingLoad = CompletableDeferred()

        viewModel.loadMore()

        assertEquals(1, state.articles.size)
        assertEquals(DiscoverPhase.LoadingMore, state.phase)

        repository.completeLoad(Outcome.Success(ArticlePage(emptyList(), null)))
    }

    // ----- End of feed --------------------------------------------------------

    @Test
    fun anAbsentCursorEndsTheFeed() {
        // The only end signal: the API returns no total count.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
        assertEquals(1, state.articles.size)
    }

    @Test
    fun aFullPageWithoutCursorIsALegitimateEnd() {
        repository.enqueuePage(List(40) { article(id = it.toLong()) }, nextCursor = null)

        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
        assertEquals(40, state.articles.size)
    }

    @Test
    fun anEmptyFirstPageIsAnEmptyFeedRatherThanAnEnd() {
        // "You have read everything" under an empty list explains nothing: the
        // screen distinguishes the case from the empty list and the phase.
        repository.enqueuePage(emptyList(), nextCursor = null)

        assertTrue(state.articles.isEmpty())
        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
    }

    @Test
    fun loadMoreIsIgnoredOnceTheFeedHasEnded() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(1, repository.loadCallCount)
    }

    // ----- Failures -----------------------------------------------------------

    @Test
    fun aFailedFirstPageIsReportedWithItsCause() {
        repository.enqueueFailure(FeedError.NoNetwork)

        val failed = assertIs<DiscoverPhase.Failed>(state.phase)
        assertEquals(DiscoverFailure.NoNetwork, failed.failure)
    }

    @Test
    fun aFailedNextPageDoesNotClearWhatIsAlreadyShown() {
        // SPECS.md §4.4: clearing the list would punish the user for having
        // approached the bottom of the feed.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.ServerUnreachable)

        viewModel.loadMore()

        assertEquals(2, state.articles.size)
        val failed = assertIs<DiscoverPhase.Failed>(state.phase)
        assertEquals(DiscoverFailure.ServerUnreachable, failed.failure)
    }

    @Test
    fun theTechnicalMessageOfAnUnexpectedFailureNeverReachesTheState() {
        // It is neither translated nor understandable: its place is in the
        // logs.
        repository.enqueueFailure(FeedError.Unexpected("SSLHandshakeException"))

        val failed = assertIs<DiscoverPhase.Failed>(state.phase)
        assertEquals(DiscoverFailure.Unexpected, failed.failure)
    }

    @Test
    fun loadMoreIsIgnoredAfterAFailureSoTheRequestIsNotRepeatedForever() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.NoNetwork)

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(2, repository.loadCallCount)
    }

    @Test
    fun retryingResumesFromTheSameCursor() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.NoNetwork)
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = null)

        viewModel.loadMore()
        viewModel.retry()

        assertEquals(listOf(null, PageCursor("c1"), PageCursor("c1")), repository.requestedCursors)
        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
    }

    @Test
    fun retryingDoesNothingWhenNothingHasFailed() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))

        viewModel.retry()

        assertEquals(1, repository.loadCallCount)
    }

    // ----- End of session -----------------------------------------------------

    @Test
    fun anExpiredSessionIsNotAnErrorMessage() {
        // The repository invalidates the session and the root gate switches on
        // its own: annotating a screen about to disappear would help no one.
        repository.enqueueFailure(FeedError.SessionExpired)

        assertEquals(DiscoverPhase.SessionEnded, state.phase)
    }

    @Test
    fun anExpiredSessionStopsAskingForPages() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.SessionExpired)

        viewModel.loadMore()
        viewModel.loadMore()
        viewModel.retry()

        assertEquals(2, repository.loadCallCount)
        assertEquals(1, state.articles.size)
    }

    // ----- Automatic marking as read ------------------------------------------

    @Test
    fun anArticleIsNotReadBeforeItHasStayedLongEnoughOnScreen() {
        // Area alone is not enough: a fast scroll crosses several articles at
        // full height without any of them being read.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS - 1L)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(emptySet(), readArticles)
        assertFalse(state.articles.single().isRead)
    }

    @Test
    fun anArticleVisibleEnoughForOneSecondIsReported() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 0.6f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 0.6f))

        assertEquals(setOf(ArticleId(1L)), readArticles)
    }

    @Test
    fun aReadArticleStaysInPlaceAndOnlyItsFlagChanges() {
        // SPECS.md §4.5: making it disappear would move the content under the
        // finger of whoever is reading.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = null)

        markAsRead(ArticleId(2L))

        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
        assertEquals(listOf(false, true), state.articles.map { it.isRead })
    }

    @Test
    fun anArticleThatLeavesTheScreenRestartsItsCountdownFromZero() {
        // "Continuous" is read literally: ten passes of 100 ms do not make one
        // second of reading.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS - 1L)
        viewModel.onVisibilityChanged(emptyMap())
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(emptySet(), readArticles)
    }

    @Test
    fun anArticleFallingBelowTheAreaThresholdRestartsItsCountdown() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS - 1L)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 0.2f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(emptySet(), readArticles)
    }

    @Test
    fun anArticleAlreadyReportedIsNeverReportedAgain() {
        // An article stays visible for dozens of observations after crossing
        // the threshold: reporting it again would multiply network calls for
        // nothing.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        markAsRead(ArticleId(1L))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS * 5)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(1, reportedBatches.size)
        assertTrue(state.articles.single().isRead)
    }

    @Test
    fun articlesCrossingTheThresholdTogetherAreReportedAsASingleBatch() {
        // SPECS.md §4.5: marks leave in batches, not one call per article.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f, ArticleId(2L) to 0.8f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f, ArticleId(2L) to 0.8f))

        assertEquals(listOf(setOf(ArticleId(1L), ArticleId(2L))), reportedBatches)
    }

    @Test
    fun anObservationThatChangesNothingIsNotEvenReported() {
        // Observation is periodic: without this filter, the callback would
        // fire five times per second with an empty batch.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertTrue(reportedBatches.isEmpty())
    }

    // ----- Local cache (SPECS.md §5.1) ----------------------------------------

    @Test
    fun theCachedArticlesAreShownWithoutAnyNetworkRequest() {
        // SPECS.md §5.1: launch shows the cache and stops there. The automatic
        // request created a race between disk and network whose outcome
        // decided the screen.
        repository.cachedArticles.value = listOf(article(id = 1L, title = "Du cache"))

        assertEquals(listOf("Du cache"), state.articles.map { it.title })
        assertEquals(DiscoverPhase.Idle, state.phase)
        assertEquals(0, repository.loadCallCount)
    }

    @Test
    fun anEmptyCacheTriggersTheOnlyAutomaticLoad() {
        // The single exception: nothing to show (first open, return after
        // sign-out). An application with neither request nor content would be
        // dead.
        repository.enqueuePage(listOf(article(id = 1L)))

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertEquals(1, repository.loadCallCount)
    }

    @Test
    fun aCacheEmptiedLaterDoesNotTriggerAnyRequest() {
        // The bootstrap decision is made once, on the first emission: a purge
        // emptying the cache later must not launch a request behind the
        // user's back.
        repository.cachedArticles.value = listOf(article(id = 1L))
        assertEquals(DiscoverPhase.Idle, state.phase)

        repository.cachedArticles.value = emptyList()

        assertEquals(0, repository.loadCallCount)
    }

    @Test
    fun aCacheThatGrowsBeforeTheNetworkAnswersOnlyAddsWhatIsMissing() {
        // The cache flow re-emits on every write: reapplying the whole list
        // would replace what is displayed, and the reading position would jump.
        repository.pendingLoad = CompletableDeferred()
        repository.cachedArticles.value = listOf(article(id = 1L))
        assertEquals(listOf(1L), state.articles.map { it.id })

        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))

        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
    }

    @Test
    fun theFirstPageDoesNotDuplicateWhatTheCacheHasAlreadyShown() {
        // The network page, requested by scrolling and never on its own,
        // contains the same articles as the cache: only unknown ones are
        // added, and at the top, since they are the most recent.
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))
        repository.enqueuePage(listOf(article(id = 3L), article(id = 1L), article(id = 2L)), nextCursor = null)

        viewModel.loadMore()

        assertEquals(listOf(3L, 1L, 2L), state.articles.map { it.id })
    }

    @Test
    fun aPageAlreadyEntirelyShownDoesNotStopTheFeed() {
        // Without chaining, the list would stop growing without a word,
        // indistinguishable from a breakdown (SPECS.md §4.4).
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 3L)), nextCursor = null)

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
        assertEquals(listOf(null, PageCursor("c1")), repository.requestedCursors)
    }

    @Test
    fun theCacheStopsFeedingTheListOnceTheServerHasAnswered() {
        // Past the first page, order belongs to the server: a cache write must
        // no longer insert an article into a list being browsed.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(listOf(1L), state.articles.map { it.id })

        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 9L))

        assertEquals(listOf(1L), state.articles.map { it.id })
    }

    // ----- Offline (SPECS.md §5.2) --------------------------------------------

    @Test
    fun beingOfflineWithCachedArticlesKeepsThemAndRaisesTheBanner() {
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))
        repository.enqueueFailure(FeedError.NoNetwork)

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
        assertTrue(state.isOffline)
        assertTrue(state.showsOfflineBanner)
    }

    @Test
    fun beingOfflineWithoutAnyCacheShowsNoBannerToHangOn() {
        // Without articles, the absence of network is no longer a degraded
        // regime but the only thing to say: the full-frame message handles it.
        repository.enqueueFailure(FeedError.NoNetwork)

        assertTrue(state.isOffline)
        assertFalse(state.showsOfflineBanner)
        assertTrue(state.articles.isEmpty())
    }

    @Test
    fun anUnreachableServerIsNotTheOfflineRegime() {
        // A server that does not answer is an incident, not an absence of
        // network: the banner would lie about the device's state.
        repository.enqueueFailure(FeedError.ServerUnreachable)

        assertFalse(state.isOffline)
    }

    @Test
    fun aSuccessfulPageLeavesTheOfflineRegime() {
        repository.enqueueFailure(FeedError.NoNetwork)
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.retry()

        assertFalse(state.isOffline)
    }

    // ----- Refresh (SPECS.md §4.6) --------------------------------------------

    @Test
    fun refreshingReplacesTheListWithTheFreshPage() {
        // SPECS.md §4.6: the pull empties the display rather than completing
        // it. The rendered order is the repository's, with no rearrangement.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(
            listOf(article(id = 2L), article(id = 3L), article(id = 1L)),
            nextCursor = PageCursor("c9"),
        )

        viewModel.refresh()

        assertEquals(listOf(2L, 3L, 1L), state.articles.map { it.id })
        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun refreshingDropsArticlesThatAreNoLongerInTheFeed() {
        // An article read in the meantime disappears: the list is replaced,
        // not completed, so nothing keeps it on screen.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = null)

        viewModel.refresh()

        assertEquals(listOf(2L), state.articles.map { it.id })
    }

    @Test
    fun refreshingWithNothingNewChangesNothingAtAll() {
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))

        viewModel.refresh()

        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
        assertEquals(DiscoverPhase.Idle, state.phase)
    }

    @Test
    fun refreshingRestartsThePaginationFromTheFreshPage() {
        // The list having been replaced, the old cursor would point to a place
        // no longer displayed: continuation resumes from the page the pull
        // just returned.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("c9"))
        repository.enqueuePage(listOf(article(id = 3L)), nextCursor = null)

        viewModel.refresh()
        viewModel.loadMore()

        assertEquals(listOf(null, PageCursor("c9")), repository.requestedCursors)
        assertEquals(listOf(2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun theRefreshIndicatorLastsExactlyAsLongAsTheRequest() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingRefresh = CompletableDeferred()

        viewModel.refresh()
        assertTrue(state.isRefreshing)

        repository.completeRefresh(Outcome.Success(ArticlePage(listOf(article(id = 2L)), PageCursor("c9"))))

        assertFalse(state.isRefreshing)
        assertEquals(listOf(2L), state.articles.map { it.id })
    }

    @Test
    fun aSecondRefreshWhileTheFirstIsInFlightIsIgnored() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingRefresh = CompletableDeferred()

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(1, repository.refreshCallCount)
        repository.completeRefresh(Outcome.Success(ArticlePage(emptyList(), null)))
    }

    @Test
    fun noPageIsRequestedWhileARefreshIsInFlight() {
        // The two requests target the two ends of the feed: running them
        // concurrently would interleave their insertions.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingRefresh = CompletableDeferred()
        viewModel.refresh()

        viewModel.loadMore()

        assertEquals(1, repository.loadCallCount)
        repository.completeRefresh(Outcome.Success(ArticlePage(emptyList(), null)))
    }

    @Test
    fun aSuccessfulRefreshLiftsThePreviousFailure() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.NoNetwork)
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("c9"))

        viewModel.loadMore()
        viewModel.refresh()

        assertEquals(DiscoverPhase.Idle, state.phase)
        assertFalse(state.isOffline)
    }

    @Test
    fun aRefreshThatReturnsTheWholeFeedEndsIt() {
        // The phase follows the returned page, not the previous state: a page
        // without a cursor is an end of feed, wherever the pull came from.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = null)

        viewModel.refresh()

        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
    }

    @Test
    fun aRefreshThatFindsMoreReopensTheFeed() {
        // Symmetric case: the feed had ended, the server has something new.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("c9"))

        viewModel.refresh()

        assertEquals(DiscoverPhase.Idle, state.phase)
    }

    @Test
    fun aFailedRefreshKeepsTheArticlesAndSignalsTheCause() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.NoNetwork)

        viewModel.refresh()

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertTrue(state.isOffline)
        assertFalse(state.isRefreshing)
    }

    // ----- Opening an article (SPECS.md §4.7 and §5.2) -------------------------

    @Test
    fun openingAnArticleMarksItReadWhateverItsPastVisibility() {
        // No visibility observation happened: the gesture suffices.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = null)

        val opened = viewModel.onArticleOpened(2L)

        assertTrue(opened)
        assertEquals(listOf(false, true), state.articles.map { it.isRead })
        assertEquals(setOf(ArticleId(2L)), readArticles)
    }

    @Test
    fun anArticleOpenedThenScrolledPastIsNotReportedTwice() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.onArticleOpened(1L)
        markAsRead(ArticleId(1L))

        assertEquals(1, reportedBatches.size)
    }

    @Test
    fun openingAnArticleOfflineIsRefusedAndExplained() {
        // Opening the tab would only show the browser's error page, and the
        // article would pass as read without having been readable (SPECS.md §5.2).
        repository.cachedArticles.value = listOf(article(id = 1L))
        repository.enqueueFailure(FeedError.NoNetwork)
        viewModel.loadMore()

        val opened = viewModel.onArticleOpened(1L)

        assertFalse(opened)
        assertTrue(state.isOfflineOpenNoticeVisible)
        assertFalse(state.articles.single().isRead)
        assertTrue(readArticles.isEmpty())
    }

    @Test
    fun theOfflineOpeningNoticeIsAcknowledged() {
        repository.enqueueFailure(FeedError.NoNetwork)
        viewModel.onArticleOpened(1L)

        viewModel.dismissOfflineOpenNotice()

        assertFalse(state.isOfflineOpenNoticeVisible)
    }

    // ----- The feed does not shuffle at launch (SPECS.md §4.2, rule 3) --------

    @Test
    fun theFirstServerPageDoesNotReorderWhatTheCacheShowed() {
        // Rule 3 of SPECS.md §4.2: a given set of articles always appears in
        // the same order. The cache displays first (§5.1), and the following
        // network page carries the same articles in server order; reapplying
        // them would make the reading position jump under the finger.
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 3L), article(id = 1L), article(id = 2L)))

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun theFirstServerPageAddsWhatIsNewWithoutMovingTheRest() {
        // Unknown articles go on top: they are more recent, and placing them
        // at the bottom would show them far from their date. What was already
        // displayed keeps its order and relative place.
        repository.cachedArticles.value = listOf(article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L), article(id = 3L)))

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun theFirstServerPageRemovesNothingThatWasShown() {
        // A cached article absent from the network page stays displayed:
        // making it disappear would remove what was being read.
        repository.cachedArticles.value = listOf(article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 1L)))

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun aSecondCacheEmissionDoesNotShuffleTheFeedEither() {
        // The cache flow re-emits on every write, hence after each received
        // page. Consuming it again would reposition articles in an order the
        // server did not dictate, in the middle of a reading session.
        repository.cachedArticles.value = listOf(article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 1L)))
        viewModel.loadMore()
        val shownAfterFirstPage = state.articles.map { it.id }

        repository.cachedArticles.value = listOf(article(id = 3L), article(id = 2L), article(id = 9L))

        assertEquals(shownAfterFirstPage, state.articles.map { it.id })
    }

    @Test
    fun aLoadedPageDoesNotResetTheReadingTimers() {
        // Behavior the GOAL-014-T13 regression broke in production: an article
        // watched during a load must stay marked read, otherwise the server
        // returns it on the next open and the feed appears to change on its
        // own.
        //
        // This test does not reproduce the regression: the fake settings
        // repository is a `StateFlow`, which never re-emits an equal value.
        // `SettingsStoreTest` covers the cause (it fails if
        // `distinctUntilChanged` is removed); this one keeps the effect.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)

        repository.enqueuePage(listOf(article(id = 2L)))
        viewModel.loadMore()
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(setOf(ArticleId(1L)), readArticles)
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
    fun aFeedNeverRefreshedInvitesToNothing() {
        repository.enqueuePage(listOf(article(id = 1L)))

        assertFalse(state.showsStaleNotice)
    }

    @Test
    fun offlineTheOfflineBannerSpeaksAloneAboutAnOldFeed() {
        // Offering "Refresh" would open a door leading nowhere, and stack a
        // second strip on top of the refused-opening one.
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.cachedArticles.value = listOf(article(id = 1L))
        repository.enqueueFailure(FeedError.NoNetwork)
        viewModel.loadMore()

        assertTrue(state.isStaleNoticeAvailable)
        assertFalse(state.showsStaleNotice)
        assertTrue(state.showsOfflineBanner)
    }

    @Test
    fun anEmptyFeedInvitesToNothing() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(emptyList(), nextCursor = null)

        assertTrue(state.isStaleNoticeAvailable)
        assertFalse(state.showsStaleNotice)
    }

    @Test
    fun nothingIsSaidWhileTheRefreshIsUnderWay() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(listOf(article(id = 1L)))
        assertTrue(state.showsStaleNotice)
        repository.pendingRefresh = CompletableDeferred()

        viewModel.refresh()

        assertTrue(state.isRefreshing)
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
    fun aSilencedInvitationDoesNotComeBackOnItsOwn() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(listOf(article(id = 1L)))
        viewModel.dismissStaleNotice()

        dispatcher.scheduler.advanceTimeBy(TWELVE_HOURS_MILLIS)

        assertFalse(state.showsStaleNotice)
    }

    @Test
    fun aSilencedInvitationComesBackOnceTheNextRefreshHasGrownOld() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(listOf(article(id = 1L)))
        viewModel.dismissStaleNotice()

        // Server contact is recorded by the article repository (GOAL-014-T03);
        // here it is set directly, then six hours pass.
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = clock.nowEpochMillis()))
        clock.advanceBy(SIX_HOURS_MILLIS)
        dispatcher.scheduler.advanceTimeBy(SIX_HOURS_MILLIS)

        assertTrue(state.showsStaleNotice)
    }

    @Test
    fun theFeedGrowsOldWithoutAnyEventAtAll() {
        // The threshold is crossed with the app open and the screen off:
        // without a periodic wake-up, the notice would only appear at the next
        // gesture.
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = clock.nowEpochMillis()))
        repository.enqueuePage(listOf(article(id = 1L)))
        assertFalse(state.showsStaleNotice)

        clock.advanceBy(SIX_HOURS_MILLIS)
        dispatcher.scheduler.advanceTimeBy(SIX_HOURS_MILLIS)

        assertTrue(state.showsStaleNotice)
    }

    @Test
    fun theInvitationBorrowsTheExistingRefresh() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(listOf(article(id = 1L)))
        repository.enqueuePage(listOf(article(id = 2L)))

        // The strip's action has no path of its own: it is the refresh of
        // SPECS.md §4.6, otherwise the two would diverge.
        viewModel.refresh()

        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun aFreshServerContactClearsTheInvitation() {
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = staleSince()))
        repository.enqueuePage(listOf(article(id = 1L)))
        assertTrue(state.showsStaleNotice)

        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = clock.nowEpochMillis()))

        assertFalse(state.showsStaleNotice)
    }

    /** A server contact timestamp old enough for the notice to be due. */
    private fun staleSince(): Long = clock.nowEpochMillis() - SEVEN_HOURS_MILLIS

    /** Takes [id] past both thresholds, with two observations one second apart. */
    private fun markAsRead(id: ArticleId) {
        viewModel.onVisibilityChanged(mapOf(id to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(id to 1f))
    }
}
