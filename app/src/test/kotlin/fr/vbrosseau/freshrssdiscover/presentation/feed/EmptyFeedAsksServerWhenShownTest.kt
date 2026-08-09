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
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * Un écran sans article interroge le serveur quand il vient au premier plan
 * (SPECS.md §5.1, GOAL-025).
 *
 * **Le cul-de-sac que ces cas ferment.** SPECS.md §5.1 veut qu'aucune requête ne
 * parte tant qu'il y a quelque chose à montrer ; la réciproque — s'il n'y a rien,
 * demander — n'était appliquée qu'une fois, au premier échantillon du cache. Tout
 * lire puis revenir sur le flux laissait donc un écran vide qui ne demandait
 * plus rien, et dont on ne sortait qu'en trouvant le bouton de la barre de titre.
 * Signalé par l'auteur.
 *
 * **Ce que les gardes empêchent compte autant que ce que la règle déclenche**, et
 * c'est pourquoi les trois refus ont leur cas. Celui du premier chargement en vol
 * est le plus important des trois : au démarrage à cache vide, l'amorçage lance
 * déjà une requête, et l'écran vient au premier plan dans le même souffle — sans
 * la garde, chaque lancement d'application partirait en double.
 *
 * Les deux modes ont chacun leurs cas : la règle vit dans deux ViewModels, et un
 * correctif appliqué d'un seul côté les ferait diverger (ARCHITECTURE.md §9.6).
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

    private fun discoverViewModel() = DiscoverViewModel(
        articleRepository = repository,
        readSyncRepository = readSyncRepository,
        settingsRepository = settingsRepository,
        freshnessRepository = freshnessRepository,
        clock = clock,
    )

    private fun swipeViewModel() = SwipeViewModel(
        articleRepository = repository,
        readSyncRepository = readSyncRepository,
        settingsRepository = settingsRepository,
        freshnessRepository = freshnessRepository,
        clock = clock,
    )

    // ----- La règle -----------------------------------------------------------

    @Test
    fun listModeAsksTheServerWhenItComesBackWithNothingToShow() {
        // Cache vide, serveur sans rien à rendre : l'amorçage aboutit sur une
        // fin de flux sans article — l'écran du lecteur qui a tout lu.
        val viewModel = discoverViewModel()
        assertEquals(DiscoverPhase.EndOfFeed, viewModel.uiState.value.phase, "état de départ attendu")

        viewModel.onScreenShown()

        assertEquals(
            1,
            repository.refreshCallCount,
            "un écran vide qui revient au premier plan doit interroger le serveur",
        )
    }

    @Test
    fun swipeModeAsksTheServerWhenItComesBackWithNothingToShow() {
        val viewModel = swipeViewModel()

        viewModel.onScreenShown()

        assertEquals(
            1,
            repository.refreshCallCount,
            "le mode Balayage porte la même règle : une pile vide y est plus nue encore",
        )
    }

    /**
     * Deux venues au premier plan valent deux tentatives, et c'est voulu : la
     * règle est accrochée à un fait ponctuel, pas à l'état de vide — lequel
     * subsiste quand le serveur n'a rien rendu, et redemanderait sans fin.
     */
    @Test
    fun eachReturnToTheForegroundIsWorthOneAttempt() {
        val viewModel = discoverViewModel()

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(2, repository.refreshCallCount)
    }

    // ----- Les trois refus ----------------------------------------------------

    @Test
    fun aFeedWithSomethingToShowAsksNothing() {
        repository.cachedArticles.value = listOf(article(id = 1L))
        val viewModel = discoverViewModel()

        viewModel.onScreenShown()

        assertEquals(
            0,
            repository.refreshCallCount,
            "SPECS.md §5.1 : aucune requête tant qu'il y a quelque chose à montrer",
        )
    }

    @Test
    fun aFirstLoadAlreadyInFlightIsNotDoubled() {
        // Le démarrage à cache vide : l'amorçage a lancé sa requête, et l'écran
        // vient au premier plan avant qu'elle ne réponde. Sans la garde, chaque
        // lancement d'application partirait en double.
        repository.pendingLoad = CompletableDeferred()
        val viewModel = discoverViewModel()
        assertEquals(DiscoverPhase.InitialLoading, viewModel.uiState.value.phase, "état de départ attendu")

        viewModel.onScreenShown()

        assertEquals(0, repository.refreshCallCount, "la requête d'amorçage est déjà en vol")
    }

    @Test
    fun aFailedLoadKeepsItsOwnRetry() {
        // Sans cette garde, un réseau absent serait martelé à chaque retour sur
        // l'écran, alors que la reprise est déjà offerte à l'utilisateur.
        repository.enqueueFailure(FeedError.NoNetwork)
        val viewModel = discoverViewModel()

        viewModel.onScreenShown()

        assertEquals(0, repository.refreshCallCount, "l'échec porte déjà sa reprise")
    }
}
