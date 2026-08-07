package fr.vbrosseau.freshrssdiscover.presentation.swipe

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.height
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
import kotlin.test.assertTrue

/** Assez d'articles pour que le chargement anticipé ne soit pas vrai d'emblée. */
private const val LONG_FEED_SIZE = 10

/** Rang à partir duquel une page de dix articles réclame la suivante. */
private const val PREFETCH_TRIGGER_PAGE = 7

/** Cible tactile minimale exigée par SPECS.md §7.1. */
private const val MIN_TOUCH_TARGET_DP = 48

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
    ) {
        composeRule.setContent {
            SwipeScreen(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onArticleClick = onArticleClick,
                pagerState = rememberPagerState(initialPage = initialPage) { uiState.pageCount },
            )
        }
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

    // ----- Accessibilité (GOAL-012-T07) ---------------------------------------

    @Test
    fun theFeedIsEntirelyNavigableWithoutAnyGesture() {
        // SPECS.md §7.1 : un balayage horizontal n'est praticable ni par un
        // lecteur d'écran, qui réserve ce geste, ni par qui manque de mobilité.
        show(feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")))

        composeRule.onNodeWithTag(SwipeTestTags.NEXT).performClick()

        composeRule.onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun theBackButtonReturnsToThePreviousArticle() {
        show(
            feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")),
            initialPage = 1,
        )

        composeRule.onNodeWithTag(SwipeTestTags.PREVIOUS).performClick()

        composeRule.onNodeWithText("Premier").assertIsDisplayed()
    }

    @Test
    fun thereIsNothingBeforeTheFirstArticleAndTheButtonSaysSo() {
        show(feedOf(uiArticle(id = 1L), uiArticle(id = 2L)))

        composeRule.onNodeWithTag(SwipeTestTags.PREVIOUS).assertIsNotEnabled()
        composeRule.onNodeWithTag(SwipeTestTags.NEXT).assertIsEnabled()
    }

    @Test
    fun theNavigationButtonsAreLargeEnoughToBeTouched() {
        show(feedOf(uiArticle(id = 1L), uiArticle(id = 2L)))

        val height = composeRule.onNodeWithTag(SwipeTestTags.NEXT).getBoundsInRoot().height

        assertTrue(height.value >= MIN_TOUCH_TARGET_DP, "hauteur mesurée : $height")
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
        composeRule.onNodeWithTag(SwipeTestTags.PREVIOUS).assertIsEnabled()
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
