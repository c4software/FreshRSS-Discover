package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * A page still in flight does not survive the refresh that disowned it
 * (GOAL-028).
 *
 * The race: `refresh()` never checks the loading lock, so a page requested
 * before the refresh stays in flight and nothing cancels it. When it returned
 * after the refresh, it was appended under the refreshed list and its cursor
 * overwrote the refresh's cursor, silently resuming the abandoned traversal.
 * Since GOAL-027, its pass through the cache also reinserted rows the refresh
 * had just removed.
 *
 * The trigger is ordinary: the top-bar button stays pressable during a
 * `LoadingMore`; a slow network is enough.
 *
 * Why the Fake has two distinct locks: the race requires a page suspended
 * while a refresh completes. A `pendingLoad` suspending both calls would make
 * the arrival order, the whole point of these cases, unobservable. Hence
 * `pendingRefresh`, which holds back only the refresh.
 *
 * The cursor is checked via [FakeArticleRepository.requestedCursors]: the
 * displayed list is not enough, since a refresh replacing it would mask the
 * cursor overwrite. The defect would only show on the next page, served from
 * the wrong end of the feed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshDiscardsStalePagesTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_MILLIS)

    private fun feedViewModel(): FeedViewModel {
        // First feed page, served at startup on an empty cache.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        return FeedViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    @Test
    fun theReloadDropsThePageThatWasInFlightWhenTheReloadArrived() {
        val viewModel = feedViewModel()
        repository.pendingLoad = CompletableDeferred()
        viewModel.loadMore()

        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("r1"))
        viewModel.refresh()
        // The page requested before the refresh arrives after it.
        repository.completeLoad(Outcome.Success(ArticlePage(listOf(article(id = 3L)), PageCursor("c3"))))

        assertEquals(
            listOf(2L),
            viewModel.uiState.value.articles.map { it.id },
            "la page périmée ne doit pas s'ajouter sous la liste rafraîchie",
        )

        // Pagination must follow the refresh's cursor, not the disowned
        // page's: the silent overwrite the list alone would not show.
        repository.enqueuePage(listOf(article(id = 4L)), nextCursor = null)
        viewModel.loadMore()
        assertEquals(PageCursor("r1"), repository.requestedCursors.last())
    }

    /**
     * A stale failure is discarded like a stale success: reporting it would
     * mark as broken a feed that was just replaced in front of the user.
     */
    @Test
    fun theReloadDropsAStaleFailureToo() {
        val viewModel = feedViewModel()
        repository.pendingLoad = CompletableDeferred()
        viewModel.loadMore()

        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = null)
        viewModel.refresh()
        repository.completeLoad(Outcome.Failure(FeedError.NoNetwork))

        assertEquals(DiscoverPhase.EndOfFeed, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.isOffline, "l'échec d'une requête désavouée ne dit rien du régime")
    }

    /**
     * The other entry point of the same race, once specific to Immersive
     * mode: its `loadMore` did not check `isRefreshing` (List mode did), so
     * the pager could start a page during the refresh with the old
     * traversal's cursor. Exactly the divergence ARCHITECTURE.md §9.6 says
     * to track, and the reason there is one ViewModel now.
     */
    @Test
    fun noPageStartsDuringAReload() {
        val viewModel = feedViewModel()
        val loadsBefore = repository.loadCallCount

        repository.pendingRefresh = CompletableDeferred()
        viewModel.refresh()
        viewModel.loadMore()

        assertEquals(loadsBefore, repository.loadCallCount, "aucune page ne part pendant un rechargement")
        repository.completeRefresh(Outcome.Success(ArticlePage(emptyList(), null)))
    }
}
