package fr.vbrosseau.freshrssdiscover.presentation.swipe

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import fr.vbrosseau.freshrssdiscover.presentation.LoadingTestTags
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.LOADABLE_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.UNREACHABLE_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.installFakeImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.discover.resetImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.feed.RefreshTestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Assez d'articles pour que le chargement anticipé ne soit pas vrai d'emblée. */
private const val LONG_FEED_SIZE = 10

/** Rang à partir duquel une page de dix articles réclame la suivante. */
private const val PREFETCH_TRIGGER_PAGE = 7

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
        onRefresh: () -> Unit = {},
    ) {
        composeRule.setContent {
            SwipeScreen(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onArticleClick = onArticleClick,
                onRefresh = onRefresh,
                pagerState = rememberPagerState(initialPage = initialPage) { uiState.pageCount },
            )
        }
    }

    // ----- Rechargement (SPECS.md §4.6) ---------------------------------------

    @Test
    fun theReloadButtonIsReachableWithoutAnyGesture() {
        // C'est la seule commande de ce mode : en plein écran il n'y a pas de
        // liste à tirer, et le tirage se superposerait au balayage.
        var reloaded = false
        show(feedOf(uiArticle(id = 1L)), onRefresh = { reloaded = true })

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).performClick()

        assertTrue(reloaded)
    }

    @Test
    fun theReloadButtonIsStillThereWhenThereIsNothingToRead() {
        // Un flux vide est justement le moment où l'on veut recharger : cacher
        // la commande là laisserait l'utilisateur sans recours.
        show(SwipeUiState(phase = DiscoverPhase.EndOfFeed))

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).assertIsDisplayed()
    }

    @Test
    fun aSecondPressIsIgnoredWhileTheReloadIsRunning() {
        // Le bouton reste visible pendant le rechargement — il montre l'attente
        // au lieu de disparaître — mais il ne redéclenche rien.
        var reloads = 0
        show(feedOf(uiArticle(id = 1L)).copy(isRefreshing = true), onRefresh = { reloads++ })

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).performClick()

        assertEquals(0, reloads)
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
    fun openingAnArticleReportsTheArticleShown() {
        var opened: Long? = null
        show(feedOf(uiArticle(id = 42L)), onArticleClick = { opened = it })

        composeRule.onNodeWithTag(SwipeTestTags.OPEN).performClick()

        assertEquals(42L, opened)
    }

    @Test
    fun anArticleWithoutAnyLinkSaysSoRatherThanOfferingADeadButton() {
        show(feedOf(uiArticle(id = 1L, isOpenable = false)))

        composeRule.onNodeWithTag(SwipeTestTags.NO_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(SwipeTestTags.OPEN).assertDoesNotExist()
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

    private fun uiArticle(
        id: Long = 1L,
        title: String = "Un titre",
        imageUrl: String? = null,
        isOpenable: Boolean = true,
    ): ArticleUiModel = ArticleUiModel(
        id = id,
        title = title,
        feedTitle = "Le Monde",
        publishedAt = RelativeTime.Hours(2),
        excerpt = "Un extrait.",
        imageUrl = imageUrl,
        isOpenable = isOpenable,
    )
}
