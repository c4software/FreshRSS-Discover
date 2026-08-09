package fr.vbrosseau.freshrssdiscover.presentation.swipe

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.presentation.LoadingTestTags
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.LOADABLE_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.UNREACHABLE_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.installFakeImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.discover.resetImageLoader
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Assez d'articles pour que le chargement anticipé ne soit pas vrai d'emblée. */
private const val LONG_FEED_SIZE = 10

/** Rang à partir duquel une page de dix articles réclame la suivante. */
private const val PREFETCH_TRIGGER_PAGE = 7

/** Cadence des relevés de visibilité, reprise de `sampleVisibility`. */
private const val SAMPLING_PERIOD_MILLIS = 200L

/** Cible tactile minimale exigée par SPECS.md §7.1. */
private val MIN_TOUCH_TARGET = 48.dp

@RunWith(RobolectricTestRunner::class)
class SwipeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun installImageLoader() = installFakeImageLoader()

    @After
    fun restoreImageLoader() = resetImageLoader()

    private fun show(
        uiState: SwipeUiState,
        initialPage: Int = 0,
        onLoadMore: () -> Unit = {},
        onRetry: () -> Unit = {},
        onArticleClick: (Long) -> Unit = {},
        onArticleShare: (Long) -> Unit = {},
        onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
    ) {
        composeRule.setContent {
            SwipeScreen(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onArticleClick = onArticleClick,
                onArticleShare = onArticleShare,
                pagerState = rememberPagerState(initialPage = initialPage) { uiState.pageCount },
                onVisibilityChanged = onVisibilityChanged,
            )
        }
    }

    // ----- Alimentation du marquage (GOAL-012-T01) ----------------------------

    @Test
    fun theArticleOnScreenIsReportedAsFullyVisible() {
        // Le maillon que ni `SwipeViewModelTest` ni `SwipeVisibilityTest` ne
        // voient : le premier suppose qu'on lui parle, le second calcule sans
        // que personne l'appelle. Sans ce relevé, rien ne serait jamais marqué
        // comme lu en mode Balayage — et tout le reste passerait au vert.
        val reports = mutableListOf<Map<ArticleId, Float>>()
        show(feedOf(uiArticle(id = 1L), uiArticle(id = 2L)), onVisibilityChanged = reports::add)

        assertEquals(mapOf(ArticleId(1L) to 1f), reports.lastOrNull())
    }

    @Test
    fun theArticleIsStillReportedWhileNothingMoves() {
        // C'est **le** piège de ce mode : un article plein écran immobile ne
        // produit aucun événement, et la règle de SPECS.md §4.5 porte sur une
        // durée — que le détecteur ne mesure que d'un relevé à l'autre. Un
        // relevé unique à l'affichage ne marquerait donc jamais rien.
        val reports = mutableListOf<Map<ArticleId, Float>>()
        composeRule.mainClock.autoAdvance = false
        show(feedOf(uiArticle(id = 1L)), onVisibilityChanged = reports::add)

        composeRule.mainClock.advanceTimeBy(SAMPLING_PERIOD_MILLIS * 3)

        assertTrue(reports.size >= 2, "relevés obtenus : ${reports.size}")
        assertTrue(reports.all { it == mapOf(ArticleId(1L) to 1f) }, "relevés : $reports")
    }

    @Test
    fun theSecondArticleIsReportedOnceTheSwipeIsDone() {
        // Le relevé suit le balayage : sans cela, le premier article resterait
        // le seul jamais signalé, et le flux ne se marquerait qu'une fois.
        val reports = mutableListOf<Map<ArticleId, Float>>()
        show(feedOf(uiArticle(id = 1L), uiArticle(id = 2L)), onVisibilityChanged = reports::add)

        composeRule.onNodeWithTag(SwipeTestTags.PAGER).performTouchInput { swipeLeft() }
        composeRule.waitUntil { reports.lastOrNull() == mapOf(ArticleId(2L) to 1f) }

        assertEquals(mapOf(ArticleId(2L) to 1f), reports.lastOrNull())
    }

    @Test
    fun nothingIsReportedWhenNoOneIsListening() {
        // `null` signifie « personne n'écoute » : armer la boucle ferait tourner
        // un minuteur pour jeter son résultat, et occuperait les
        // prévisualisations en permanence.
        composeRule.mainClock.autoAdvance = false
        show(feedOf(uiArticle(id = 1L)), onVisibilityChanged = null)

        composeRule.mainClock.advanceTimeBy(SAMPLING_PERIOD_MILLIS * 3)

        composeRule.onNodeWithTag(SwipeTestTags.page(1L)).assertExists()
    }

    // ----- Un article à la fois -----------------------------------------------

    @Test
    fun theFirstArticleIsShownFullScreen() {
        show(feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")))

        composeRule.onNodeWithText("Premier").assertIsDisplayed()
        composeRule.onNodeWithText("Second").assertDoesNotExist()
    }

    @Test
    fun anArticleShowsItsSourceItsAgeAndItsExcerpt() {
        show(feedOf(uiArticle(id = 1L, title = "Premier")))

        composeRule.onNodeWithText("Le Monde", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("il y a 2 h", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Un extrait.").assertIsDisplayed()
    }

    @Test
    fun swipingLeftShowsTheNextArticle() {
        show(feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")))

        composeRule.onNodeWithTag(SwipeTestTags.PAGER).performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun swipingRightComesBackToThePreviousArticle() {
        show(
            feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")),
            initialPage = 1,
        )

        composeRule.onNodeWithTag(SwipeTestTags.PAGER).performTouchInput { swipeRight() }

        composeRule.onNodeWithText("Premier").assertIsDisplayed()
    }

    // ----- Chargement anticipé (GOAL-012-T02) ---------------------------------

    @Test
    fun theNextPageIsRequestedBeforeReachingTheLastArticle() {
        var loadMoreCalls = 0

        show(
            longFeed(),
            initialPage = PREFETCH_TRIGGER_PAGE,
            onLoadMore = { loadMoreCalls++ },
        )

        assertTrue(loadMoreCalls > 0)
    }

    @Test
    fun theNextPageIsNotRequestedFromTheStartOfALongFeed() {
        var loadMoreCalls = 0

        show(longFeed(), onLoadMore = { loadMoreCalls++ })

        assertEquals(0, loadMoreCalls)
    }

    // ----- Fin de flux (GOAL-012-T03) -----------------------------------------

    @Test
    fun theEndOfTheFeedIsAnnouncedAfterTheLastArticle() {
        show(
            feedOf(uiArticle(id = 1L), uiArticle(id = 2L), phase = DiscoverPhase.EndOfFeed),
            initialPage = 2,
        )

        composeRule.onNodeWithTag(SwipeTestTags.END_OF_FEED).assertIsDisplayed()
    }

    @Test
    fun theWaitForTheNextPageIsShownRatherThanAWall() {
        show(
            feedOf(uiArticle(id = 1L), uiArticle(id = 2L), phase = DiscoverPhase.LoadingMore),
            initialPage = 2,
        )

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertIsDisplayed()
    }

    @Test
    fun aFailedNextPageOffersToRetryWithoutLosingTheArticles() {
        var retried = false
        show(
            feedOf(
                uiArticle(id = 1L, title = "Premier"),
                phase = DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable),
            ),
            initialPage = 1,
            onRetry = { retried = true },
        )

        composeRule.onNodeWithTag(SwipeTestTags.RETRY).performClick()

        assertTrue(retried)
        // Les articles déjà chargés sont toujours là : l'échec de la page
        // suivante n'a pas remplacé le flux par un écran d'erreur, et un
        // balayage en arrière les retrouve.
        composeRule.onNodeWithTag(SwipeTestTags.PAGER).performTouchInput { swipeRight() }
        composeRule.onNodeWithText("Premier").assertIsDisplayed()
    }

    // ----- Flux sans article --------------------------------------------------

    @Test
    fun anEmptyFeedExplainsItselfRatherThanShowingNothing() {
        show(SwipeUiState(phase = DiscoverPhase.EndOfFeed))

        composeRule.onNodeWithTag(SwipeTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(SwipeTestTags.PAGER).assertDoesNotExist()
    }

    @Test
    fun aFirstPageThatFailsShowsItsCauseAndItsRecovery() {
        show(SwipeUiState(phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork)))

        composeRule.onNodeWithTag(SwipeTestTags.FAILURE).assertIsDisplayed()
        composeRule.onNodeWithTag(SwipeTestTags.RETRY).assertIsDisplayed()
    }

    @Test
    fun theFirstLoadingShowsAnIndicatorAndNothingToSwipe() {
        show(SwipeUiState(phase = DiscoverPhase.InitialLoading))

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(SwipeTestTags.PAGER).assertDoesNotExist()
    }

    // ----- Ouverture et illustration ------------------------------------------

    @Test
    fun tappingTheCardOpensTheArticleShown() {
        var opened: Long? = null
        show(feedOf(uiArticle(id = 42L)), onArticleClick = { opened = it })

        composeRule.onNodeWithTag(SwipeTestTags.page(42L)).performClick()

        assertEquals(42L, opened)
    }

    @Test
    fun anArticleWithoutAnyLinkSaysSoAndStaysInert() {
        var opened: Long? = null
        show(feedOf(uiArticle(id = 1L, isOpenable = false)), onArticleClick = { opened = it })

        composeRule.onNodeWithTag(SwipeTestTags.NO_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(SwipeTestTags.page(1L)).performClick()

        assertNull(opened)
    }

    /**
     * Ce que craignait le bouton d'ouverture qu'on vient de retirer : un appui
     * pris pour une ouverture pendant un balayage hésitant. Compose distingue
     * le `tap` du `drag` — encore faut-il que quelqu'un le constate, sur la
     * carte réellement rendue cliquable.
     */
    @Test
    fun swipingLeftStillWorksWithAClickableCard() {
        var opened: Long? = null
        show(
            feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")),
            onArticleClick = { opened = it },
        )

        composeRule.onNodeWithTag(SwipeTestTags.PAGER).performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("Second").assertIsDisplayed()
        assertNull(opened, "le balayage a été pris pour une ouverture")
    }

    // ----- Partage de la carte (SPECS.md §4.3) --------------------------------

    @Test
    fun anArticleWithALinkCanBeShared() {
        val shared = mutableListOf<Long>()
        show(feedOf(uiArticle(id = 42L)), onArticleShare = { shared += it })

        composeRule.onNodeWithTag(SwipeTestTags.share(42L)).performClick()

        assertEquals(listOf(42L), shared)
    }

    @Test
    fun anArticleWithoutLinkCarriesNoShareButton() {
        show(feedOf(uiArticle(id = 1L, isOpenable = false)))

        composeRule.onNodeWithTag(SwipeTestTags.share(1L)).assertDoesNotExist()
    }

    @Test
    fun theShareButtonAnnouncesItselfAndIsLargeEnoughToTouch() {
        show(feedOf(uiArticle(id = 1L)))

        composeRule.onNodeWithContentDescription("Partager l'article").assertExists()

        val bounds = composeRule.onNodeWithTag(SwipeTestTags.share(1L)).getBoundsInRoot()

        assertTrue(bounds.width >= MIN_TOUCH_TARGET, "largeur ${bounds.width}")
        assertTrue(bounds.height >= MIN_TOUCH_TARGET, "hauteur ${bounds.height}")
    }

    @Test
    fun anIllustratedArticleShowsItsImage() {
        show(feedOf(uiArticle(id = 1L, imageUrl = LOADABLE_IMAGE_URL)))

        composeRule.onNodeWithTag(SwipeTestTags.ILLUSTRATION, useUnmergedTree = true).assertExists()
    }

    @Test
    fun anIllustrationThatFailsToLoadLeavesNoHole() {
        show(feedOf(uiArticle(id = 1L, imageUrl = UNREACHABLE_IMAGE_URL)))

        composeRule.onNodeWithTag(SwipeTestTags.ILLUSTRATION, useUnmergedTree = true).assertDoesNotExist()
    }

    // ----- Fabriques ----------------------------------------------------------

    private fun feedOf(
        vararg articles: ArticleUiModel,
        phase: DiscoverPhase = DiscoverPhase.EndOfFeed,
    ): SwipeUiState = SwipeUiState(articles = articles.toList(), phase = phase)

    private fun longFeed(): SwipeUiState = SwipeUiState(
        articles = (1..LONG_FEED_SIZE).map { uiArticle(id = it.toLong(), title = "Article $it") },
        phase = DiscoverPhase.Idle,
    )

    // ----- Flux ancien (SPECS.md §4.6) ----------------------------------------

    @Test
    fun anOldFeedInvitesToReloadIt() {
        showStale()

        composeRule.onNodeWithTag(SwipeTestTags.STALE_NOTICE).assertExists()
    }

    @Test
    fun aFeedThatIsNotOldSaysNothing() {
        show(SwipeUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.Idle))

        composeRule.onNodeWithTag(SwipeTestTags.STALE_NOTICE).assertDoesNotExist()
    }

    @Test
    fun theInvitationBorrowsTheExistingReload() {
        var refreshed = 0
        showStale(onRefresh = { refreshed++ })

        composeRule.onNodeWithTag(SwipeTestTags.STALE_NOTICE_REFRESH).performClick()

        assertEquals(1, refreshed)
    }

    @Test
    fun theInvitationCanBeSilencedWithoutReloading() {
        var silenced = 0
        showStale(onStaleNoticeDismiss = { silenced++ })

        composeRule.onNodeWithTag(SwipeTestTags.STALE_NOTICE_DISMISS).performClick()

        assertEquals(1, silenced)
    }

    @Test
    fun theInvitationDoesNotCoverTheShareAction() {
        // En plein écran la bandelette se pose sur la carte : elle ne doit pas
        // recouvrir la seule commande de ce mode depuis que la carte entière
        // ouvre l'article (SPECS.md §4.7).
        showStale()

        val notice = composeRule.onNodeWithTag(SwipeTestTags.STALE_NOTICE).getBoundsInRoot()
        val share = composeRule.onNodeWithTag(SwipeTestTags.share(1L)).getBoundsInRoot()

        assertTrue(
            share.bottom <= notice.top,
            "la commande de partage est recouverte par la bandelette",
        )
    }

    @Test
    fun theInvitationLeavesTheShareActionReachableOnALongArticle() {
        // Le contenu de la carte défile : sur un extrait long, le bouton de
        // partage n'est pas à l'écran au repos, mais il doit pouvoir y venir
        // entièrement. Une bandelette posée par-dessus la carte le
        // recouvrirait là où le défilement s'arrête.
        showStale(excerpt = "Un paragraphe interminable. ".repeat(60))

        composeRule.onNodeWithTag(SwipeTestTags.share(1L)).performScrollTo()

        val notice = composeRule.onNodeWithTag(SwipeTestTags.STALE_NOTICE).getBoundsInRoot()
        val share = composeRule.onNodeWithTag(SwipeTestTags.share(1L)).getBoundsInRoot()
        assertTrue(
            share.bottom <= notice.top,
            "la commande de partage reste sous la bandelette même défilée à fond",
        )
    }

    /**
     * Un flux ancien, avec de quoi lire : l'invitation y est due.
     *
     * Point d'entrée propre à ces cas plutôt qu'un [show] élargi : le
     * rechargement et l'acquittement ne concernent qu'eux.
     */
    private fun showStale(
        onRefresh: () -> Unit = {},
        onStaleNoticeDismiss: () -> Unit = {},
        excerpt: String = "Un extrait.",
    ) {
        composeRule.setContent {
            SwipeScreen(
                uiState = SwipeUiState(
                    articles = listOf(uiArticle(excerpt = excerpt)),
                    phase = DiscoverPhase.Idle,
                    isStaleNoticeAvailable = true,
                ),
                onLoadMore = {},
                onRetry = {},
                onArticleClick = {},
                onArticleShare = {},
                onRefresh = onRefresh,
                onStaleNoticeDismiss = onStaleNoticeDismiss,
            )
        }
    }

    private fun uiArticle(
        id: Long = 1L,
        title: String = "Un titre",
        imageUrl: String? = null,
        isOpenable: Boolean = true,
        excerpt: String = "Un extrait.",
        publishedAtEpochSeconds: Long = 1_700_000_000L,
    ): ArticleUiModel = ArticleUiModel(
        id = id,
        title = title,
        feedTitle = "Le Monde",
        publishedAt = RelativeTime.Hours(2),
        excerpt = excerpt,
        publishedAtEpochSeconds = publishedAtEpochSeconds,
        imageUrl = imageUrl,
        isOpenable = isOpenable,
    )
}
