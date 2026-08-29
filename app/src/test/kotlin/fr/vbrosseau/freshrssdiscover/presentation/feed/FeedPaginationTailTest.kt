package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * The page tail travels with the caller (GOAL-029): it lives in the engine,
 * with the cursor, not in the repository, a singleton shared by both
 * presentation modes where it mixed one mode's traversal with the other's.
 * Tested on List mode; the engine being shared, Immersive follows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedPaginationTailTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val repository = FakeArticleRepository()

    /** Lazy: the ViewModel loads on creation, on `Dispatchers.Main`. */
    private val viewModel: FeedViewModel by lazy {
        FeedViewModel(
            articleRepository = repository,
            readSyncRepository = FakeReadSyncRepository(),
            settingsRepository = FakeSettingsRepository(),
            freshnessRepository = FakeFeedFreshnessRepository(),
            clock = FakeClock(NOW_MILLIS),
        )
    }

    @Test
    fun theNextPageCarriesTheTailOfThePreviousOne() {
        // Rule 4 of SPECS.md §4.2: the junction is judged against the last
        // rendered article.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 3L)), nextCursor = null)

        viewModel.loadMore()

        assertEquals(listOf(2L), repository.requestedTails.last().map { it.id.value })
    }

    @Test
    fun afterAReloadTheTailIsTheOneOfTheRefreshedPage() {
        // The tail follows the traversal the cursor follows: after a reload,
        // the next page extends the refreshed page.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 5L), article(id = 6L)), nextCursor = PageCursor("c9"))
        repository.enqueuePage(listOf(article(id = 7L)), nextCursor = null)

        viewModel.refresh()
        viewModel.loadMore()

        assertEquals(listOf(6L), repository.requestedTails.last().map { it.id.value })
    }
}
