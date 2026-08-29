package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.EXCERPT_MAX_LENGTH
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NOW_MILLIS = 1_700_000_000_000L

private val VISIBILITY_THRESHOLD_MILLIS = ReadingSettings.Default.continuousVisibilityMillis

/**
 * What one feed state, served to both modes, guarantees (SPECS.md §4.8,
 * GOAL-043).
 *
 * Kept apart from [FeedViewModelTest], which is at the size limit: these
 * cases are the ones the immersive mode used to carry on its own ViewModel,
 * and the one the author reported when that ViewModel still existed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedSharedStateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_MILLIS)

    private val viewModel: FeedViewModel by lazy {
        FeedViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    private val state get() = viewModel.uiState.value

    private val reportedBatches: List<Set<ArticleId>> get() = readSyncRepository.markCalls

    // ----- One state for both modes (SPECS.md §4.8, GOAL-043) -----------------

    @Test
    fun anArticleCarriesBothExcerptsSoEitherModeCanShowIt() {
        // One projection for one state: the List reads the card excerpt, the
        // Immersive page the full-screen one, from the same article.
        val summary = "mot ".repeat(500)
        repository.enqueuePage(listOf(article(id = 1L, summary = summary)))

        val shown = state.articles.single()
        assertTrue(shown.excerpt.length <= EXCERPT_MAX_LENGTH + 1)
        assertTrue(shown.immersiveExcerpt.length > EXCERPT_MAX_LENGTH)
    }

    @Test
    fun reloadingKeepsBothExcerpts() {
        // A divergence that actually existed: the refresh projected with the
        // List excerpt only, and a reloaded immersive feed showed pages
        // truncated to the card's length.
        val summary = "mot ".repeat(500)
        repository.enqueuePage(listOf(article(id = 1L, summary = summary)))
        repository.enqueuePage(listOf(article(id = 2L, summary = summary)))

        viewModel.refresh()

        assertTrue(state.articles.single().immersiveExcerpt.length > EXCERPT_MAX_LENGTH)
    }

    @Test
    fun aReloadThatFindsNothingLeavesNothingToShowInEitherMode() {
        // The reported defect: the List emptied by a reload while the
        // Immersive mode, on its own instance, kept the previous articles.
        // With one instance there is one list, and it is empty.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)))
        repository.enqueuePage(emptyList(), nextCursor = null)

        viewModel.refresh()

        assertTrue(state.articles.isEmpty())
        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
    }

    @Test
    fun aFirstShowingOnAFilledCacheAsksNothing() {
        // The quiet launch (SPECS.md §5.1): arriving on the feed with
        // something to show, whichever mode shows it, requests nothing
        // (GOAL-042).
        repository.enqueuePage(listOf(article(id = 1L)))
        repository.enqueuePage(listOf(article(id = 2L)))
        viewModel.onScreenShown()

        assertEquals(0, repository.refreshCallCount)
        assertEquals(listOf(1L), state.articles.map { it.id })
    }

    @Test
    fun comingBackToAReadArticleDoesNotMarkItAgain() {
        // GOAL-012-T04: flicking back neither unmarks nor re-reports; that
        // would be a request for nothing.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)))
        hold(id = 1L)

        hold(id = 2L)
        hold(id = 1L)

        assertEquals(listOf(setOf(ArticleId(1L)), setOf(ArticleId(2L))), reportedBatches)
        assertTrue(state.articles.first { it.id == 1L }.isRead)
    }

    /** Holds an article full screen for the required duration, then reports again. */
    private fun hold(id: Long) {
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to 1f))
    }
}
