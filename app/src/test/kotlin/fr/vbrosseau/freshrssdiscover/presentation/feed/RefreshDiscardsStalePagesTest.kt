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
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * Une page encore en vol ne survit pas au rechargement qui l'a désavouée
 * (GOAL-028).
 *
 * **La course.** `refresh()` ne regarde jamais [le verrou de chargement] : une
 * page demandée **avant** le tirage reste en vol, et rien ne l'annule. Revenue
 * après le rechargement, elle s'ajoutait sous la liste rafraîchie et son
 * curseur **écrasait celui du rechargement** — la pagination reprenait en
 * silence le parcours abandonné. Depuis GOAL-027, son passage par le cache y
 * réinsérait de surcroît des lignes que le renouvellement venait d'emporter.
 *
 * Le déclencheur est ordinaire : le bouton de la barre de titre reste pressable
 * pendant un `LoadingMore`, il suffit d'un réseau lent.
 *
 * **Pourquoi deux verrous distincts dans la Fake.** La course exige une page
 * suspendue *pendant* qu'un rechargement aboutit : un `pendingLoad` qui
 * suspendrait les deux appels rendrait l'ordre d'arrivée — tout l'objet de ces
 * cas — inobservable. D'où `pendingRefresh`, qui ne retient que le
 * rechargement.
 *
 * Le curseur est éprouvé par [FakeArticleRepository.requestedCursors] : la
 * liste affichée ne suffit pas, un rechargement qui la remplace masquerait
 * l'écrasement du curseur — le défaut ne se verrait qu'à la page suivante,
 * servie depuis le mauvais bout du flux.
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

    private fun discoverViewModel(): DiscoverViewModel {
        // La première page du flux, servie à l'amorçage sur cache vide.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        return DiscoverViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    private fun swipeViewModel(): SwipeViewModel {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        return SwipeViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    @Test
    fun listModeDropsThePageThatWasInFlightWhenTheReloadArrived() {
        val viewModel = discoverViewModel()
        repository.pendingLoad = CompletableDeferred()
        viewModel.loadMore()

        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("r1"))
        viewModel.refresh()
        // La page partie avant le tirage arrive **après** lui.
        repository.completeLoad(Outcome.Success(ArticlePage(listOf(article(id = 3L)), PageCursor("c3"))))

        assertEquals(
            listOf(2L),
            viewModel.uiState.value.articles.map { it.id },
            "la page périmée ne doit pas s'ajouter sous la liste rafraîchie",
        )

        // Et la pagination suit le curseur du rechargement, pas celui de la
        // page désavouée — c'est l'écrasement silencieux que la liste seule ne
        // montrerait pas.
        repository.enqueuePage(listOf(article(id = 4L)), nextCursor = null)
        viewModel.loadMore()
        assertEquals(PageCursor("r1"), repository.requestedCursors.last())
    }

    /**
     * L'échec périmé est jeté comme le succès : le signaler peindrait en panne
     * un flux qui vient d'être remplacé sous les yeux de l'utilisateur.
     */
    @Test
    fun listModeDropsAStaleFailureToo() {
        val viewModel = discoverViewModel()
        repository.pendingLoad = CompletableDeferred()
        viewModel.loadMore()

        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = null)
        viewModel.refresh()
        repository.completeLoad(Outcome.Failure(FeedError.NoNetwork))

        assertEquals(DiscoverPhase.EndOfFeed, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.isOffline, "l'échec d'une requête désavouée ne dit rien du régime")
    }

    @Test
    fun swipeModeDropsThePageThatWasInFlightWhenTheReloadArrived() {
        val viewModel = swipeViewModel()
        repository.pendingLoad = CompletableDeferred()
        viewModel.loadMore()

        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = PageCursor("r1"))
        viewModel.refresh()
        repository.completeLoad(Outcome.Success(ArticlePage(listOf(article(id = 3L)), PageCursor("c3"))))

        assertEquals(listOf(2L), viewModel.uiState.value.articles.map { it.id })

        repository.enqueuePage(listOf(article(id = 4L)), nextCursor = null)
        viewModel.loadMore()
        assertEquals(PageCursor("r1"), repository.requestedCursors.last())
    }

    /**
     * L'autre porte de la même course, propre au Balayage : `loadMore` n'y
     * vérifiait pas `isRefreshing` — le mode Liste le faisait — et le pagineur
     * pouvait donc **démarrer** une page pendant le rechargement, avec le
     * curseur de l'ancien parcours. La divergence exacte qu'ARCHITECTURE.md
     * §9.6 dit de traquer.
     */
    @Test
    fun swipeModeStartsNoPageDuringAReload() {
        val viewModel = swipeViewModel()
        val loadsBefore = repository.loadCallCount

        repository.pendingRefresh = CompletableDeferred()
        viewModel.refresh()
        viewModel.loadMore()

        assertEquals(loadsBefore, repository.loadCallCount, "aucune page ne part pendant un rechargement")
        repository.completeRefresh(Outcome.Success(ArticlePage(emptyList(), null)))
    }
}
