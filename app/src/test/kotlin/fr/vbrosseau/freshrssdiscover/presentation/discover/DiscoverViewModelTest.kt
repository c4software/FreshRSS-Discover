package fr.vbrosseau.freshrssdiscover.presentation.discover

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

class DiscoverViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeArticleRepository()
    private val clock = FakeClock(NOW_SECONDS * 1_000L)

    /**
     * Construit **paresseusement** : le ViewModel charge sa première page dès
     * sa création, sur `Dispatchers.Main`. Un initialiseur de propriété
     * s'exécuterait avant que [MainDispatcherRule] ne l'ait substitué.
     */
    private val viewModel: DiscoverViewModel by lazy {
        DiscoverViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            clock = clock,
        )
    }

    /** Reçoit les lots de marquage : c'est lui qui remplace l'ancien rappel. */
    private val readSyncRepository = FakeReadSyncRepository()

    private val settingsRepository = FakeSettingsRepository()

    private val state get() = viewModel.uiState.value

    /** Un élément par appel du rappel : c'est le lotissement lui-même qui est vérifié. */
    /** Les lots transmis au dépôt de synchronisation, dans l'ordre d'émission. */
    private val reportedBatches: List<Set<ArticleId>> get() = readSyncRepository.markCalls

    private val readArticles: Set<ArticleId> get() = reportedBatches.flatten().toSet()

    // ----- Premier chargement -------------------------------------------------

    @Test
    fun theFirstPageIsRequestedWithoutAnyCursor() {
        // Seul `null` demande le début du flux : un curseur vide relancerait la
        // première page sans que rien ne le signale.
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
        // Jamais de `System.currentTimeMillis()` : sans horloge injectée, la
        // date affichée dépendrait de l'heure de la machine.
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
        // Le défilement appelle `loadMore()` à chaque image : sans idempotence,
        // une même page serait demandée plusieurs fois.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        // Force la construction du ViewModel : sa première page doit aboutir
        // avant qu'un chargement suspendu ne soit armé.
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
        // Un indicateur qui remplacerait la liste ferait perdre la lecture en
        // cours.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingLoad = CompletableDeferred()

        viewModel.loadMore()

        assertEquals(1, state.articles.size)
        assertEquals(DiscoverPhase.LoadingMore, state.phase)

        repository.completeLoad(Outcome.Success(ArticlePage(emptyList(), null)))
    }

    // ----- Fin de flux --------------------------------------------------------

    @Test
    fun anAbsentCursorEndsTheFeed() {
        // C'est le seul signal de fin : l'API ne renvoie aucun compteur total.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
        assertFalse(state.isEmptyFeed)
    }

    @Test
    fun aFullPageWithoutCursorIsALegitimateEnd() {
        repository.enqueuePage(List(40) { article(id = it.toLong()) }, nextCursor = null)

        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
        assertEquals(40, state.articles.size)
    }

    @Test
    fun anEmptyFirstPageIsAnEmptyFeedRatherThanAnEnd() {
        // « Vous avez tout lu » sous une liste vide n'explique rien.
        repository.enqueuePage(emptyList(), nextCursor = null)

        assertTrue(state.isEmptyFeed)
    }

    @Test
    fun loadMoreIsIgnoredOnceTheFeedHasEnded() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(1, repository.loadCallCount)
    }

    // ----- Échecs -------------------------------------------------------------

    @Test
    fun aFailedFirstPageIsReportedWithItsCause() {
        repository.enqueueFailure(FeedError.NoNetwork)

        val failed = assertIs<DiscoverPhase.Failed>(state.phase)
        assertEquals(DiscoverFailure.NoNetwork, failed.failure)
    }

    @Test
    fun aFailedNextPageDoesNotClearWhatIsAlreadyShown() {
        // SPECS.md §4.4 : effacer la liste punirait l'utilisateur de s'être
        // approché du bas du flux.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueueFailure(FeedError.ServerUnreachable)

        viewModel.loadMore()

        assertEquals(2, state.articles.size)
        val failed = assertIs<DiscoverPhase.Failed>(state.phase)
        assertEquals(DiscoverFailure.ServerUnreachable, failed.failure)
    }

    @Test
    fun theTechnicalMessageOfAnUnexpectedFailureNeverReachesTheState() {
        // Il n'est ni traduit ni compréhensible : sa place est dans les
        // journaux.
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

    // ----- Fin de session -----------------------------------------------------

    @Test
    fun anExpiredSessionIsNotAnErrorMessage() {
        // Le dépôt invalide la session et l'aiguillage racine bascule de
        // lui-même : commenter un écran sur le point de disparaître n'aiderait
        // personne.
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

    // ----- Marquage automatique comme lu --------------------------------------

    @Test
    fun anArticleIsNotReadBeforeItHasStayedLongEnoughOnScreen() {
        // La seule surface ne suffit pas : un défilement rapide traverse
        // plusieurs articles à pleine hauteur sans qu'aucun soit lu.
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
        // SPECS.md §4.5 : le faire disparaître déplacerait le contenu sous le
        // doigt de qui est en train de lire.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = null)

        markAsRead(ArticleId(2L))

        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
        assertEquals(listOf(false, true), state.articles.map { it.isRead })
    }

    @Test
    fun anArticleThatLeavesTheScreenRestartsItsCountdownFromZero() {
        // « Continue » se lit littéralement : dix passages de 100 ms ne font pas
        // une seconde de lecture.
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
        // Un article reste visible pendant des dizaines d'observations après
        // avoir franchi le seuil : le resignaler multiplierait les appels
        // réseau pour rien.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        markAsRead(ArticleId(1L))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS * 5)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(1, reportedBatches.size)
        assertTrue(state.articles.single().isRead)
    }

    @Test
    fun articlesCrossingTheThresholdTogetherAreReportedAsASingleBatch() {
        // SPECS.md §4.5 : le marquage part par lots, pas un appel par article.
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f, ArticleId(2L) to 0.8f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f, ArticleId(2L) to 0.8f))

        assertEquals(listOf(setOf(ArticleId(1L), ArticleId(2L))), reportedBatches)
    }

    @Test
    fun anObservationThatChangesNothingIsNotEvenReported() {
        // L'observation est périodique : sans ce filtre, le rappel partirait
        // cinq fois par seconde avec un lot vide.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertTrue(reportedBatches.isEmpty())
    }

    /** Amène [id] au-delà des deux seuils, en deux observations séparées d'une seconde. */
    private fun markAsRead(id: ArticleId) {
        viewModel.onVisibilityChanged(mapOf(id to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(id to 1f))
    }
}
