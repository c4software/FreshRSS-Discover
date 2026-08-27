package fr.vbrosseau.freshrssdiscover.presentation.immersive

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Enough articles so the prefetch is not immediately true. */
private const val LONG_FEED_SIZE = 10

/** Index from which a ten-article page requests the next one. */
private const val PREFETCH_TRIGGER_PAGE = 7

/** Sampling period of the visibility reports, taken from `sampleVisibility`. */
private const val SAMPLING_PERIOD_MILLIS = 200L

/** Minimum touch target required by SPECS.md §7.1. */
private val MIN_TOUCH_TARGET = 48.dp

/*
 * Locale pinned to French, like the screenshot harness (ARCHITECTURE.md §8.2).
 *
 * These cases assert literal labels, and the UI is bilingual since
 * GOAL-021-T02: French lives in `values-fr/`, English in `values/`. Without
 * this qualifier, Robolectric renders the default language (English) and every
 * assertion fails.
 *
 * The content of `values/` is covered elsewhere by a dedicated `en-rUS` case
 * (`EnglishStringsTest`): without it, a string missed in translation would
 * only show on an English-locale device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class ImmersiveScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun installImageLoader() = installFakeImageLoader()

    @After
    fun restoreImageLoader() = resetImageLoader()

    private fun show(
        uiState: ImmersiveUiState,
        initialPage: Int = 0,
        onLoadMore: () -> Unit = {},
        onRetry: () -> Unit = {},
        onArticleClick: (Long) -> Unit = {},
        onArticleShare: (Long) -> Unit = {},
        onVisibilityChanged: ((Map<ArticleId, Float>) -> Unit)? = null,
    ) {
        composeRule.setContent {
            ImmersiveScreen(
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

    // ----- Feeding the read marking (GOAL-012-T01) ----------------------------

    @Test
    fun theArticleOnScreenIsReportedAsFullyVisible() {
        // The link neither `ImmersiveViewModelTest` nor `ImmersiveVisibilityTest`
        // sees: the former assumes it is called, the latter computes without
        // anyone calling it. Without this report, nothing would ever be marked
        // read in Immersive mode, and everything else would still pass.
        val reports = mutableListOf<Map<ArticleId, Float>>()
        show(feedOf(uiArticle(id = 1L), uiArticle(id = 2L)), onVisibilityChanged = reports::add)

        assertEquals(mapOf(ArticleId(1L) to 1f), reports.lastOrNull())
    }

    @Test
    fun theArticleIsStillReportedWhileNothingMoves() {
        // The trap of this mode: a motionless full-screen article produces no
        // events, and the SPECS.md §4.5 rule is about a duration, which the
        // detector only measures from one report to the next. A single report
        // at display time would never mark anything.
        val reports = mutableListOf<Map<ArticleId, Float>>()
        composeRule.mainClock.autoAdvance = false
        show(feedOf(uiArticle(id = 1L)), onVisibilityChanged = reports::add)

        composeRule.mainClock.advanceTimeBy(SAMPLING_PERIOD_MILLIS * 3)

        assertTrue(reports.size >= 2, "relevés obtenus : ${reports.size}")
        assertTrue(reports.all { it == mapOf(ArticleId(1L) to 1f) }, "relevés : $reports")
    }

    @Test
    fun theSecondArticleIsReportedOnceTheFlickIsDone() {
        // Reporting follows the flick: otherwise the first article would stay
        // the only one ever reported, and the feed would only mark once.
        val reports = mutableListOf<Map<ArticleId, Float>>()
        show(feedOf(uiArticle(id = 1L), uiArticle(id = 2L)), onVisibilityChanged = reports::add)

        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).performTouchInput { swipeUp() }
        composeRule.waitUntil { reports.lastOrNull() == mapOf(ArticleId(2L) to 1f) }

        assertEquals(mapOf(ArticleId(2L) to 1f), reports.lastOrNull())
    }

    @Test
    fun nothingIsReportedWhenNoOneIsListening() {
        // `null` means "no one is listening": arming the loop would run a
        // timer only to discard its result, and keep previews busy forever.
        composeRule.mainClock.autoAdvance = false
        show(feedOf(uiArticle(id = 1L)), onVisibilityChanged = null)

        composeRule.mainClock.advanceTimeBy(SAMPLING_PERIOD_MILLIS * 3)

        composeRule.onNodeWithTag(ImmersiveTestTags.page(1L)).assertExists()
    }

    // ----- One article at a time ----------------------------------------------

    @Test
    fun theFirstArticleIsShownFullScreen() {
        show(feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")))

        composeRule.onNodeWithText("Premier").assertIsDisplayed()
        // Composed, not displayed: the next page is kept ready so its
        // illustration loads before the flick, but it must stay off screen.
        composeRule.onNodeWithText("Second").assertIsNotDisplayed()
    }

    @Test
    fun anArticleShowsItsSourceItsAgeAndItsExcerpt() {
        show(feedOf(uiArticle(id = 1L, title = "Premier")))

        composeRule.onNodeWithText("Le Monde", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("il y a 2 h", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Un extrait.").assertIsDisplayed()
    }

    @Test
    fun flickingUpShowsTheNextArticle() {
        show(feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")))

        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).performTouchInput { swipeUp() }

        composeRule.onNodeWithText("Second").assertIsDisplayed()
    }

    @Test
    fun flickingDownComesBackToThePreviousArticle() {
        show(
            feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")),
            initialPage = 1,
        )

        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).performTouchInput { swipeDown() }

        composeRule.onNodeWithText("Premier").assertIsDisplayed()
    }

    // ----- Pull to refresh (GOAL-039-T01) -------------------------------------

    @Test
    fun pullingDownOnTheFirstPageAsksForARefresh() {
        // The short-video convention: on the first item, a pull reloads.
        var refreshed = 0
        showPullable(initialPage = 0, onRefresh = { refreshed++ })

        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).performTouchInput {
            swipeDown(startY = centerY, endY = bottom)
        }
        composeRule.waitForIdle()

        assertEquals(1, refreshed)
    }

    @Test
    fun pullingDownOnALaterPageGoesBackOnePageAndReloadsNothing() {
        // Elsewhere the drag is the pager's: it must never both move the
        // page and empty the feed under the finger.
        var refreshed = 0
        showPullable(initialPage = 1, onRefresh = { refreshed++ })

        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).performTouchInput {
            swipeDown(startY = centerY, endY = bottom)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Premier").assertIsDisplayed()
        assertEquals(0, refreshed)
    }

    /** Two pages and a reload to observe: a dedicated entry point, as for the stale feed. */
    private fun showPullable(initialPage: Int, onRefresh: () -> Unit) {
        val uiState = feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second"))
        composeRule.setContent {
            ImmersiveScreen(
                uiState = uiState,
                onLoadMore = {},
                onRetry = {},
                onArticleClick = {},
                onArticleShare = {},
                onRefresh = onRefresh,
                pagerState = rememberPagerState(initialPage = initialPage) { uiState.pageCount },
            )
        }
    }

    // ----- Prefetch (GOAL-012-T02) --------------------------------------------

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

    // ----- End of feed (GOAL-012-T03) -----------------------------------------

    @Test
    fun theEndOfTheFeedIsAnnouncedAfterTheLastArticle() {
        show(
            feedOf(uiArticle(id = 1L), uiArticle(id = 2L), phase = DiscoverPhase.EndOfFeed),
            initialPage = 2,
        )

        composeRule.onNodeWithTag(ImmersiveTestTags.END_OF_FEED).assertIsDisplayed()
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

        composeRule.onNodeWithTag(ImmersiveTestTags.RETRY).performClick()

        assertTrue(retried)
        // Already loaded articles are still there: the next-page failure did
        // not replace the feed with an error screen, and a flick back finds
        // them.
        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).performTouchInput { swipeDown() }
        composeRule.onNodeWithText("Premier").assertIsDisplayed()
    }

    // ----- Feed without articles ----------------------------------------------

    @Test
    fun anEmptyFeedExplainsItselfRatherThanShowingNothing() {
        show(ImmersiveUiState(phase = DiscoverPhase.EndOfFeed))

        composeRule.onNodeWithTag(ImmersiveTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).assertDoesNotExist()
    }

    @Test
    fun aFirstPageThatFailsShowsItsCauseAndItsRecovery() {
        show(ImmersiveUiState(phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork)))

        composeRule.onNodeWithTag(ImmersiveTestTags.FAILURE).assertIsDisplayed()
        composeRule.onNodeWithTag(ImmersiveTestTags.RETRY).assertIsDisplayed()
    }

    @Test
    fun theFirstLoadingShowsAnIndicatorAndNothingToFlick() {
        show(ImmersiveUiState(phase = DiscoverPhase.InitialLoading))

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).assertDoesNotExist()
    }

    // ----- Opening and illustration -------------------------------------------

    @Test
    fun tappingThePageOpensTheArticleShown() {
        var opened: Long? = null
        show(feedOf(uiArticle(id = 42L)), onArticleClick = { opened = it })

        composeRule.onNodeWithTag(ImmersiveTestTags.page(42L)).performClick()

        assertEquals(42L, opened)
    }

    @Test
    fun anArticleWithoutAnyLinkSaysSoAndStaysInert() {
        var opened: Long? = null
        show(feedOf(uiArticle(id = 1L, isOpenable = false)), onArticleClick = { opened = it })

        composeRule.onNodeWithTag(ImmersiveTestTags.NO_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(ImmersiveTestTags.page(1L)).performClick()

        assertNull(opened)
    }

    /**
     * The risk the removed open button guarded against: a press taken for an
     * open during a hesitant flick. Compose distinguishes `tap` from `drag`,
     * but that must be verified on the page actually made clickable.
     */
    @Test
    fun flickingUpStillWorksWithAClickablePage() {
        var opened: Long? = null
        show(
            feedOf(uiArticle(id = 1L, title = "Premier"), uiArticle(id = 2L, title = "Second")),
            onArticleClick = { opened = it },
        )

        composeRule.onNodeWithTag(ImmersiveTestTags.PAGER).performTouchInput { swipeUp() }

        composeRule.onNodeWithText("Second").assertIsDisplayed()
        assertNull(opened, "le geste a été pris pour une ouverture")
    }

    // ----- The veil over a reload (GOAL-042-T04) ------------------------------

    @Test
    fun aReloadVeilsTheFeedAndSwallowsTaps() {
        // A tap through the veil would open an article about to be replaced.
        var opened: Long? = null
        show(feedOf(uiArticle(id = 1L)).copy(isRefreshing = true), onArticleClick = { opened = it })

        composeRule.onNodeWithTag(ImmersiveTestTags.RELOAD_VEIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ImmersiveTestTags.RELOAD_VEIL).performClick()

        assertNull(opened)
    }

    @Test
    fun withoutAReloadThereIsNoVeil() {
        show(feedOf(uiArticle(id = 1L)))

        composeRule.onNodeWithTag(ImmersiveTestTags.RELOAD_VEIL).assertDoesNotExist()
    }

    // ----- Card sharing (SPECS.md §4.3) ---------------------------------------

    @Test
    fun anArticleWithALinkCanBeShared() {
        val shared = mutableListOf<Long>()
        show(feedOf(uiArticle(id = 42L)), onArticleShare = { shared += it })

        composeRule.onNodeWithTag(ImmersiveTestTags.share(42L)).performClick()

        assertEquals(listOf(42L), shared)
    }

    @Test
    fun anArticleWithoutLinkCarriesNoShareButton() {
        show(feedOf(uiArticle(id = 1L, isOpenable = false)))

        composeRule.onNodeWithTag(ImmersiveTestTags.share(1L)).assertDoesNotExist()
    }

    @Test
    fun theShareButtonAnnouncesItselfAndIsLargeEnoughToTouch() {
        show(feedOf(uiArticle(id = 1L)))

        composeRule.onNodeWithContentDescription("Partager l'article").assertExists()

        val bounds = composeRule.onNodeWithTag(ImmersiveTestTags.share(1L)).getBoundsInRoot()

        assertTrue(bounds.width >= MIN_TOUCH_TARGET, "largeur ${bounds.width}")
        assertTrue(bounds.height >= MIN_TOUCH_TARGET, "hauteur ${bounds.height}")
    }

    @Test
    fun anIllustratedArticleShowsItsImage() {
        show(feedOf(uiArticle(id = 1L, imageUrl = LOADABLE_IMAGE_URL)))

        composeRule.onNodeWithTag(ImmersiveTestTags.ILLUSTRATION, useUnmergedTree = true).assertExists()
    }

    @Test
    fun anIllustrationThatFailsToLoadLeavesNoHole() {
        show(feedOf(uiArticle(id = 1L, imageUrl = UNREACHABLE_IMAGE_URL)))

        composeRule.onNodeWithTag(ImmersiveTestTags.ILLUSTRATION, useUnmergedTree = true).assertDoesNotExist()
    }

    // ----- Factories ----------------------------------------------------------

    private fun feedOf(
        vararg articles: ArticleUiModel,
        phase: DiscoverPhase = DiscoverPhase.EndOfFeed,
    ): ImmersiveUiState = ImmersiveUiState(articles = articles.toList(), phase = phase)

    private fun longFeed(): ImmersiveUiState = ImmersiveUiState(
        articles = (1..LONG_FEED_SIZE).map { uiArticle(id = it.toLong(), title = "Article $it") },
        phase = DiscoverPhase.Idle,
    )

    // ----- Stale feed (SPECS.md §4.6) -----------------------------------------

    @Test
    fun anOldFeedInvitesToReloadIt() {
        showStale()

        composeRule.onNodeWithTag(ImmersiveTestTags.STALE_NOTICE).assertExists()
    }

    @Test
    fun aFeedThatIsNotOldSaysNothing() {
        show(ImmersiveUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.Idle))

        composeRule.onNodeWithTag(ImmersiveTestTags.STALE_NOTICE).assertDoesNotExist()
    }

    @Test
    fun theInvitationBorrowsTheExistingReload() {
        var refreshed = 0
        showStale(onRefresh = { refreshed++ })

        composeRule.onNodeWithTag(ImmersiveTestTags.STALE_NOTICE_REFRESH).performClick()

        assertEquals(1, refreshed)
    }

    @Test
    fun theInvitationCanBeSilencedWithoutReloading() {
        var silenced = 0
        showStale(onStaleNoticeDismiss = { silenced++ })

        composeRule.onNodeWithTag(ImmersiveTestTags.STALE_NOTICE_DISMISS).performClick()

        assertEquals(1, silenced)
    }

    @Test
    fun theInvitationDoesNotCoverTheShareAction() {
        // Full screen, the strip sits under the page: it must not cover the
        // only control of this mode since the whole page opens the article
        // (SPECS.md §4.7).
        showStale()

        val notice = composeRule.onNodeWithTag(ImmersiveTestTags.STALE_NOTICE).getBoundsInRoot()
        val share = composeRule.onNodeWithTag(ImmersiveTestTags.share(1L)).getBoundsInRoot()

        assertTrue(
            share.bottom <= notice.top,
            "la commande de partage est recouverte par la bandelette",
        )
    }

    /**
     * A stale feed with something to read: the notice is due.
     *
     * A dedicated entry point rather than a widened [show]: refresh and
     * dismissal only concern these cases.
     */
    private fun showStale(
        onRefresh: () -> Unit = {},
        onStaleNoticeDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            ImmersiveScreen(
                uiState = ImmersiveUiState(
                    articles = listOf(uiArticle()),
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
    ): ArticleUiModel = ArticleUiModel(
        id = id,
        title = title,
        feedTitle = "Le Monde",
        publishedAt = RelativeTime.Hours(2),
        excerpt = excerpt,
        imageUrl = imageUrl,
        isOpenable = isOpenable,
    )
}
