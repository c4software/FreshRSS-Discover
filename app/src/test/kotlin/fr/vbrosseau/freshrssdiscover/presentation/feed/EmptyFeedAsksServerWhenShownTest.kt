package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
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

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * A screen without articles queries the server when it comes to the foreground
 * (SPECS.md §5.1, GOAL-025).
 *
 * SPECS.md §5.1 wants no request while there is something to show; the
 * converse (if there is nothing, ask) was only applied once, on the first
 * cache sample. Reading everything and returning to the feed left an empty
 * screen that no longer asked for anything.
 *
 * What the guards prevent matters as much as what the rule triggers, hence a
 * case for each of the three refusals. The in-flight first load is the most
 * important: at startup with an empty cache, the bootstrap already launches a
 * request and the screen comes to the foreground at the same moment; without
 * the guard, every application launch would go out twice.
 *
 * Both modes have their own cases: the rule lives in two ViewModels, and a
 * fix applied on one side only would make them diverge (ARCHITECTURE.md §9.6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmptyFeedAsksServerWhenShownTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_MILLIS)

    private fun feedViewModel() = FeedViewModel(
        articleRepository = repository,
        readSyncRepository = readSyncRepository,
        settingsRepository = settingsRepository,
        freshnessRepository = freshnessRepository,
        clock = clock,
    )

    // ----- The rule -----------------------------------------------------------

    @Test
    fun anEmptyFeedAsksTheServerWhenItComesBackWithNothingToShow() {
        // Empty cache, server with nothing to return: bootstrap ends in an
        // end-of-feed without articles, the screen of a reader who read it all.
        val viewModel = feedViewModel()
        assertEquals(DiscoverPhase.EndOfFeed, viewModel.uiState.value.phase, "état de départ attendu")

        viewModel.onScreenShown()

        assertEquals(
            1,
            repository.refreshCallCount,
            "un écran vide qui revient au premier plan doit interroger le serveur",
        )
    }

    /**
     * Two foreground arrivals are worth two attempts, deliberately: the rule
     * is attached to a punctual event, not to the empty state, which persists
     * when the server returned nothing and would ask again forever.
     */
    @Test
    fun eachReturnToTheForegroundIsWorthOneAttempt() {
        val viewModel = feedViewModel()

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(2, repository.refreshCallCount)
    }

    // ----- The three refusals -------------------------------------------------

    @Test
    fun aFeedWithSomethingToShowAsksNothing() {
        repository.cachedArticles.value = listOf(article(id = 1L))
        val viewModel = feedViewModel()

        viewModel.onScreenShown()

        assertEquals(
            0,
            repository.refreshCallCount,
            "SPECS.md §5.1 : aucune requête tant qu'il y a quelque chose à montrer",
        )
    }

    @Test
    fun aFirstLoadAlreadyInFlightIsNotDoubled() {
        // Startup with an empty cache: bootstrap launched its request, and the
        // screen comes to the foreground before it answers. Without the guard,
        // every application launch would go out twice.
        repository.pendingLoad = CompletableDeferred()
        val viewModel = feedViewModel()
        assertEquals(DiscoverPhase.InitialLoading, viewModel.uiState.value.phase, "état de départ attendu")

        viewModel.onScreenShown()

        assertEquals(0, repository.refreshCallCount, "la requête d'amorçage est déjà en vol")
    }

    @Test
    fun aFailedLoadKeepsItsOwnRetry() {
        // Without this guard, an absent network would be hammered on every
        // return to the screen, while a retry is already offered to the user.
        repository.enqueueFailure(FeedError.NoNetwork)
        val viewModel = feedViewModel()

        viewModel.onScreenShown()

        assertEquals(0, repository.refreshCallCount, "l'échec porte déjà sa reprise")
    }
}
