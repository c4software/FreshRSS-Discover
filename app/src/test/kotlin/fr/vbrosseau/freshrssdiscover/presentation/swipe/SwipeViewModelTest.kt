package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val NOW_SECONDS = 1_700_000_000L

/** Durée d'affichage continu exigée par SPECS.md §4.5, reprise ici pour la lisibilité des cas. */
private const val VISIBILITY_THRESHOLD_MILLIS = 1_000L

/** Un article plein écran est intégralement visible : c'est tout le propos de SPECS.md §4.8. */
private const val FULL_SCREEN = 1f

class SwipeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val clock = FakeClock(NOW_SECONDS * 1_000L)

    /**
     * Construit **paresseusement** : le ViewModel charge sa première page dès
     * sa création, sur `Dispatchers.Main`. Un initialiseur de propriété
     * s'exécuterait avant que [MainDispatcherRule] ne l'ait substitué.
     */
    private val viewModel: SwipeViewModel by lazy {
        SwipeViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            clock = clock,
        )
    }

    private val state get() = viewModel.uiState.value

    private val reportedBatches: List<Set<ArticleId>> get() = readSyncRepository.markCalls

    /** Maintient un article plein écran pendant la durée exigée, puis relève. */
    private fun watch(id: Long, millis: Long = VISIBILITY_THRESHOLD_MILLIS) {
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to FULL_SCREEN))
        clock.advanceBy(millis)
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to FULL_SCREEN))
    }

    // ----- Premier affichage --------------------------------------------------

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
        // SPECS.md §5.1 : en plein écran le défaut serait plus visible encore
        // qu'en liste — un unique écran vide, sans rien à balayer.
        repository.pendingLoad = CompletableDeferred()
        repository.cachedArticles.value = listOf(article(id = 9L, title = "Du cache"))

        assertEquals(listOf("Du cache"), state.articles.map { it.title })
    }

    @Test
    fun theFullScreenExcerptIsTheOneUsedByThisMode() {
        // Le mode Liste s'arrête à 240 caractères ; le plein écran en montre
        // davantage (SPECS.md §8, question 8).
        val summary = "mot ".repeat(500)
        repository.enqueuePage(listOf(article(id = 1L, summary = summary)))

        assertTrue(state.articles.single().excerpt.length > EXCERPT_LENGTH_OF_A_CARD)
    }

    // ----- Chargement anticipé et fin de flux ---------------------------------

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
        // Le balayage réévalue le seuil à chaque recomposition : sans verrou,
        // plusieurs requêtes partiraient avant la première mise à jour.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        // Force la construction — donc la première page — avant d'armer le
        // chargement suspendu : sinon c'est l'initialisation qui s'y prendrait.
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingLoad = CompletableDeferred()

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(2, repository.loadCallCount)
    }

    @Test
    fun theEndOfTheFeedIsAnnouncedRatherThanSuffered() {
        // GOAL-012-T03 : un balayage qui cesse de répondre est indistinguable
        // d'une panne.
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

    // ----- Erreurs ------------------------------------------------------------

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

    // ----- Marquage comme lu --------------------------------------------------

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
        // GOAL-012-T04 : le retour en arrière ne délie pas, et ne resignale
        // pas non plus — ce serait une requête pour rien.
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

    // ----- Ouverture ----------------------------------------------------------

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
        /** Borne du mode Liste (`EXCERPT_MAX_LENGTH`), citée pour la comparaison. */
        const val EXCERPT_LENGTH_OF_A_CARD = 240
    }
    // ----- Rechargement (SPECS.md §4.6) ---------------------------------------

    @Test
    fun reloadingReplacesTheWholeStackRatherThanCompletingIt() {
        // Même règle qu'en mode Liste : recharger vide, il ne complète pas.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 5L), article(id = 6L)), nextCursor = PageCursor("c9"))

        viewModel.refresh()

        assertEquals(listOf(5L, 6L), state.articles.map { it.id })
        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun reloadingRestartsThePaginationFromTheFreshPage() {
        // L'ancien curseur désignerait un endroit qui n'est plus dans la pile.
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
        repository.pendingLoad = CompletableDeferred()

        viewModel.refresh()
        assertTrue(state.isRefreshing)

        repository.completeLoad(Outcome.Success(ArticlePage(listOf(article(id = 5L)), null)))
        assertFalse(state.isRefreshing)
        assertEquals(listOf(5L), state.articles.map { it.id })
    }

    @Test
    fun asecondReloadIsIgnoredWhileOneIsAlreadyInFlight() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.pendingLoad = CompletableDeferred()

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun aFailedReloadKeepsTheStackAndSaysWhy() {
        // Rien n'est jeté avant d'avoir quelque chose à mettre à la place :
        // échouer en vidant l'écran serait perdre ce qui était lisible.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.NoNetwork)

        viewModel.refresh()

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertFalse(state.isRefreshing)
        assertIs<DiscoverPhase.Failed>(state.phase)
    }
}
