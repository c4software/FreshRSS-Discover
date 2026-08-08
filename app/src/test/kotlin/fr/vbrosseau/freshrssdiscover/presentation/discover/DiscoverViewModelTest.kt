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

/** Durée d'affichage continu exigée par SPECS.md §4.5, reprise ici pour la lisibilité des cas. */
private const val VISIBILITY_THRESHOLD_MILLIS = 1_000L

private const val ONE_HOUR_MILLIS = 60L * 60L * 1_000L
private const val SIX_HOURS_MILLIS = 6L * ONE_HOUR_MILLIS
private const val SEVEN_HOURS_MILLIS = 7L * ONE_HOUR_MILLIS
private const val TWELVE_HOURS_MILLIS = 12L * ONE_HOUR_MILLIS

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    /**
     * Gardé sous la main : les cas d'ancienneté font avancer son ordonnanceur
     * virtuel, faute de quoi le réveil périodique attendrait réellement.
     */
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

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
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    private val freshnessRepository = FakeFeedFreshnessRepository()

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

    // ----- Cache local (SPECS.md §5.1) ----------------------------------------

    @Test
    fun theCachedArticlesAreShownWithoutAnyNetworkRequest() {
        // SPECS.md §5.1 : le lancement montre le cache et s'y arrête. La
        // requête automatique créait une course entre le disque et le réseau,
        // dont l'issue décidait de l'écran.
        repository.cachedArticles.value = listOf(article(id = 1L, title = "Du cache"))

        assertEquals(listOf("Du cache"), state.articles.map { it.title })
        assertEquals(DiscoverPhase.Idle, state.phase)
        assertEquals(0, repository.loadCallCount)
    }

    @Test
    fun anEmptyCacheTriggersTheOnlyAutomaticLoad() {
        // L'unique exception : rien à montrer — première ouverture, retour
        // après déconnexion. Une application sans requête ni contenu serait
        // morte.
        repository.enqueuePage(listOf(article(id = 1L)))

        assertEquals(listOf(1L), state.articles.map { it.id })
        assertEquals(1, repository.loadCallCount)
    }

    @Test
    fun aCacheEmptiedLaterDoesNotTriggerAnyRequest() {
        // La décision d'amorçage se prend une fois, sur la première émission :
        // une purge qui viderait le cache ensuite ne doit pas lancer de
        // requête dans le dos de l'utilisateur.
        repository.cachedArticles.value = listOf(article(id = 1L))
        assertEquals(DiscoverPhase.Idle, state.phase)

        repository.cachedArticles.value = emptyList()

        assertEquals(0, repository.loadCallCount)
    }

    @Test
    fun aCacheThatGrowsBeforeTheNetworkAnswersOnlyAddsWhatIsMissing() {
        // Le flux du cache réémet à chaque écriture : réappliquer la liste
        // entière remplacerait ce qui est affiché, et la lecture sauterait.
        repository.pendingLoad = CompletableDeferred()
        repository.cachedArticles.value = listOf(article(id = 1L))
        assertEquals(listOf(1L), state.articles.map { it.id })

        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))

        assertEquals(listOf(1L, 2L), state.articles.map { it.id })
    }

    @Test
    fun theFirstPageDoesNotDuplicateWhatTheCacheHasAlreadyShown() {
        // La page réseau — demandée par le défilement, plus jamais toute
        // seule — contient les mêmes articles que le cache : seuls les
        // inconnus s'ajoutent, et en tête — ce sont les plus récents.
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))
        repository.enqueuePage(listOf(article(id = 3L), article(id = 1L), article(id = 2L)), nextCursor = null)

        viewModel.loadMore()

        assertEquals(listOf(3L, 1L, 2L), state.articles.map { it.id })
    }

    @Test
    fun aPageAlreadyEntirelyShownDoesNotStopTheFeed() {
        // Sans enchaînement, la liste cesserait de s'allonger sans rien dire —
        // indistinguable d'une panne (SPECS.md §4.4).
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 3L)), nextCursor = null)

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
        assertEquals(listOf(null, PageCursor("c1")), repository.requestedCursors)
    }

    @Test
    fun theCacheStopsFeedingTheListOnceTheServerHasAnswered() {
        // Passé la première page, l'ordre appartient au serveur : une écriture
        // de cache ne doit plus insérer d'article dans une liste parcourue.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(listOf(1L), state.articles.map { it.id })

        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 9L))

        assertEquals(listOf(1L), state.articles.map { it.id })
    }

    // ----- Hors ligne (SPECS.md §5.2) -----------------------------------------

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
        // Sans article, l'absence de réseau n'est plus un régime dégradé mais
        // la seule chose à dire : le message plein cadre s'en charge.
        repository.enqueueFailure(FeedError.NoNetwork)

        assertTrue(state.isOffline)
        assertFalse(state.showsOfflineBanner)
        assertTrue(state.articles.isEmpty())
    }

    @Test
    fun anUnreachableServerIsNotTheOfflineRegime() {
        // Le serveur qui ne répond pas est un incident, pas une absence de
        // réseau : le bandeau mentirait sur l'état de l'appareil.
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

    // ----- Rafraîchissement (SPECS.md §4.6) -----------------------------------

    @Test
    fun refreshingReplacesTheListWithTheFreshPage() {
        // SPECS.md §4.6 : le tirage vide l'affichage plutôt que de le compléter.
        // L'ordre rendu est celui du dépôt, sans réarrangement.
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
        // Un article devenu lu entre-temps disparaît : la liste est remplacée,
        // pas complétée, donc rien ne le maintient à l'écran.
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
        // La liste ayant été remplacée, l'ancien curseur désignerait un endroit
        // qui n'est plus affiché : la suite se redéroule à partir de la page
        // que le tirage vient de rendre.
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
        repository.pendingLoad = CompletableDeferred()

        viewModel.refresh()
        assertTrue(state.isRefreshing)

        repository.completeLoad(Outcome.Success(ArticlePage(listOf(article(id = 2L)), PageCursor("c9"))))

        assertFalse(state.isRefreshing)
        assertEquals(listOf(2L), state.articles.map { it.id })
    }

    @Test
    fun aSecondRefreshWhileTheFirstIsInFlightIsIgnored() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingLoad = CompletableDeferred()

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(1, repository.refreshCallCount)
        repository.completeLoad(Outcome.Success(ArticlePage(emptyList(), null)))
    }

    @Test
    fun noPageIsRequestedWhileARefreshIsInFlight() {
        // Les deux requêtes portent sur les deux bouts du flux : les mener de
        // front mêlerait leurs insertions.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        assertEquals(DiscoverPhase.Idle, state.phase)
        repository.pendingLoad = CompletableDeferred()
        viewModel.refresh()

        viewModel.loadMore()

        assertEquals(1, repository.loadCallCount)
        repository.completeLoad(Outcome.Success(ArticlePage(emptyList(), null)))
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
        // La phase suit la page rendue, et non l'état précédent : une page sans
        // curseur est une fin de flux, quel que soit l'endroit d'où l'on tire.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        repository.enqueuePage(listOf(article(id = 2L)), nextCursor = null)

        viewModel.refresh()

        assertEquals(DiscoverPhase.EndOfFeed, state.phase)
    }

    @Test
    fun aRefreshThatFindsMoreReopensTheFeed() {
        // Symétrique : le flux s'était terminé, le serveur a du neuf.
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

    // ----- Ouverture d'un article (SPECS.md §4.7 et §5.2) ----------------------

    @Test
    fun openingAnArticleMarksItReadWhateverItsPastVisibility() {
        // Aucune observation de visibilité n'a eu lieu : le geste suffit.
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
        // Ouvrir l'onglet n'afficherait que la page d'erreur du navigateur, et
        // l'article passerait pour lu sans avoir pu l'être (SPECS.md §5.2).
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

    // ----- Le flux ne se mélange pas au lancement (SPECS.md §4.2, règle 3) ----

    @Test
    fun theFirstServerPageDoesNotReorderWhatTheCacheShowed() {
        // La règle 3 de SPECS.md §4.2 : un même ensemble d'articles se présente
        // toujours dans le même ordre. Le cache affiche d'abord (§5.1), et la
        // page réseau qui suit porte les **mêmes** articles dans l'ordre du
        // serveur — les réappliquer ferait sauter la lecture sous le doigt.
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 3L), article(id = 1L), article(id = 2L)))

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun theFirstServerPageAddsWhatIsNewWithoutMovingTheRest() {
        // Les inconnus vont en tête — ils sont plus récents, et les poser en bas
        // les montrerait très loin de leur date. Mais ce qui était déjà affiché
        // garde son ordre, à sa place relative.
        repository.cachedArticles.value = listOf(article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 1L), article(id = 2L), article(id = 3L)))

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun theFirstServerPageRemovesNothingThatWasShown() {
        // Un article du cache absent de la page réseau reste affiché : le faire
        // disparaître retirerait sous les yeux ce qu'on était en train de lire.
        repository.cachedArticles.value = listOf(article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 1L)))

        viewModel.loadMore()

        assertEquals(listOf(1L, 2L, 3L), state.articles.map { it.id })
    }

    @Test
    fun aSecondCacheEmissionDoesNotShuffleTheFeedEither() {
        // Le flux du cache réémet à chaque écriture, donc après chaque page
        // reçue. Le consommer à nouveau replacerait des articles dans un ordre
        // que le serveur n'a pas dicté, au milieu d'une lecture.
        repository.cachedArticles.value = listOf(article(id = 2L), article(id = 3L))
        repository.enqueuePage(listOf(article(id = 1L)))
        viewModel.loadMore()
        val shownAfterFirstPage = state.articles.map { it.id }

        repository.cachedArticles.value = listOf(article(id = 3L), article(id = 2L), article(id = 9L))

        assertEquals(shownAfterFirstPage, state.articles.map { it.id })
    }

    @Test
    fun aLoadedPageDoesNotResetTheReadingTimers() {
        // Le comportement que la régression de GOAL-014-T13 cassait en
        // production : un article regardé pendant un chargement doit rester
        // marqué lu, sans quoi le serveur le renvoie à l'ouverture suivante et
        // le flux paraît changer tout seul.
        //
        // ⚠️ Ce test ne **reproduit** pas la régression : le dépôt de réglages
        // factice est un `StateFlow`, qui ne réémet jamais une valeur égale.
        // C'est `SettingsStoreTest` qui tient la cause — il échoue si l'on
        // retire `distinctUntilChanged`, vérifié. Celui-ci garde l'effet.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)

        repository.enqueuePage(listOf(article(id = 2L)))
        viewModel.loadMore()
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(setOf(ArticleId(1L)), readArticles)
    }

    // ----- Ancienneté du flux (SPECS.md §4.6) ---------------------------------

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
        // Proposer « Rafraîchir » ouvrirait une porte qui ne mène nulle part,
        // et empilerait une seconde bandelette sur celle de l'ouverture refusée.
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
        repository.pendingLoad = CompletableDeferred()

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

        // Le contact serveur est noté par le dépôt d'articles (GOAL-014-T03) ;
        // ici on le pose directement, puis on laisse passer six heures.
        freshnessRepository.set(FeedFreshness(lastRefreshEpochMillis = clock.nowEpochMillis()))
        clock.advanceBy(SIX_HOURS_MILLIS)
        dispatcher.scheduler.advanceTimeBy(SIX_HOURS_MILLIS)

        assertTrue(state.showsStaleNotice)
    }

    @Test
    fun theFeedGrowsOldWithoutAnyEventAtAll() {
        // Le seuil se franchit application ouverte et écran éteint : sans
        // réveil périodique, l'avis n'apparaîtrait qu'au prochain geste.
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

        // L'action de la bandelette n'a pas de chemin à elle : c'est le
        // rafraîchissement de SPECS.md §4.6, sans quoi les deux divergeraient.
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

    /** Un horodatage de contact serveur assez vieux pour que l'avis soit dû. */
    private fun staleSince(): Long = clock.nowEpochMillis() - SEVEN_HOURS_MILLIS

    /** Amène [id] au-delà des deux seuils, en deux observations séparées d'une seconde. */
    private fun markAsRead(id: ArticleId) {
        viewModel.onVisibilityChanged(mapOf(id to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(id to 1f))
    }
}
