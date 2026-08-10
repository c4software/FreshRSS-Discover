package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import fr.vbrosseau.freshrssdiscover.presentation.LoadingTestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Locale pinned to French, like the capture harness (ARCHITECTURE.md §8.2).
 *
 * These cases assert literal labels, and the UI is bilingual since
 * GOAL-021-T02: French lives in `values-fr/`, English in `values/`. Without
 * this qualifier, Robolectric renders the default language (English) and every
 * assertion fails. Same decision as for the captures, whose references are
 * French.
 *
 * The content of `values/` is tested elsewhere by a dedicated `en-rUS` case
 * (`EnglishStringsTest`): without it, a string missed in translation would
 * only show on an English-speaking device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class DiscoverScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun installImageLoader() = installFakeImageLoader()

    @After
    fun restoreImageLoader() = resetImageLoader()

    private fun show(
        uiState: DiscoverUiState,
        onLoadMore: () -> Unit = {},
        onRetry: () -> Unit = {},
        onArticleClick: (Long) -> Unit = {},
        onRefresh: () -> Unit = {},
        onOfflineNoticeDismiss: () -> Unit = {},
        onStaleNoticeDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            DiscoverScreen(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onArticleClick = onArticleClick,
                onArticleShare = {},
                onRefresh = onRefresh,
                onOfflineNoticeDismiss = onOfflineNoticeDismiss,
                onStaleNoticeDismiss = onStaleNoticeDismiss,
            )
        }
    }

    // ----- Article presentation -----------------------------------------------

    @Test
    fun anArticleShowsItsTitleItsSourceAndItsAge() {
        // Without the feed name, the mix of sources would be confusing.
        show(DiscoverUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.EndOfFeed))

        composeRule.onNodeWithText("Un titre").assertExists()
        composeRule.onNodeWithText("Le Monde", substring = true).assertExists()
        composeRule.onNodeWithText("il y a 2 h", substring = true).assertExists()
        composeRule.onNodeWithText("Un extrait.").assertExists()
    }

    @Test
    fun anArticleWithoutIllustrationLeavesNoEmptySpace() {
        // SPECS.md §4.3: no empty frame, no generic placeholder image.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle(imageUrl = null)),
                phase = DiscoverPhase.EndOfFeed,
            ),
        )

        composeRule.onNodeWithTag(DiscoverTestTags.ILLUSTRATION, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Un titre").assertExists()
    }

    @Test
    fun anIllustratedArticleShowsItsImage() {
        show(
            DiscoverUiState(
                articles = listOf(uiArticle(imageUrl = LOADABLE_IMAGE_URL)),
                phase = DiscoverPhase.EndOfFeed,
            ),
        )

        composeRule.onNodeWithTag(DiscoverTestTags.ILLUSTRATION, useUnmergedTree = true).assertExists()
    }

    @Test
    fun anIllustrationThatFailsToLoadLeavesNoHole() {
        // An image that cannot be fetched is, to the reader, indistinguishable
        // from an article that has none: the slot closes (SPECS.md §4.3).
        show(
            DiscoverUiState(
                articles = listOf(uiArticle(imageUrl = UNREACHABLE_IMAGE_URL)),
                phase = DiscoverPhase.EndOfFeed,
            ),
        )

        composeRule.onNodeWithTag(DiscoverTestTags.ILLUSTRATION, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Un titre").assertExists()
    }

    @Test
    fun anArticleAnnouncingAnIllustrationWithoutUrlShowsNothing() {
        // Degraded case: the feed announces an image without giving its
        // address. There is nothing to load, so nothing to reserve.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle(imageUrl = null, hasIllustration = true)),
                phase = DiscoverPhase.EndOfFeed,
            ),
        )

        composeRule.onNodeWithTag(DiscoverTestTags.ILLUSTRATION, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theIllustrationHeightComesFromTheCardWidthAndNotFromTheImage() {
        // The fake image is square: if the slot height depended on it, the
        // measured ratio would be 1. Seeing it hold at 16/9 proves the list
        // will not shift when the image arrives.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle(imageUrl = LOADABLE_IMAGE_URL)),
                phase = DiscoverPhase.EndOfFeed,
            ),
        )

        val bounds = composeRule
            .onNodeWithTag(DiscoverTestTags.ILLUSTRATION, useUnmergedTree = true)
            .getBoundsInRoot()
        val ratio = bounds.width.value / bounds.height.value

        assertTrue(abs(ratio - 16f / 9f) < RATIO_TOLERANCE, "rapport d'aspect mesuré : $ratio")
    }

    @Test
    fun theIllustrationIsMarkedAsDecorative() {
        // SPECS.md §7.1 allows either a description or a decorative marking:
        // the latter is chosen, and the absence of a semantic description is
        // what materializes it.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle(imageUrl = LOADABLE_IMAGE_URL)),
                phase = DiscoverPhase.EndOfFeed,
            ),
        )

        composeRule
            .onNodeWithTag(DiscoverTestTags.ILLUSTRATION, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun anArticleWithALinkIsClickable() {
        val clicked = mutableListOf<Long>()
        show(
            DiscoverUiState(articles = listOf(uiArticle(id = 7L)), phase = DiscoverPhase.EndOfFeed),
            onArticleClick = { clicked += it },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.card(7L)).performClick()

        assertEquals(listOf(7L), clicked)
    }

    @Test
    fun anArticleWithoutLinkIsNotClickableAndSaysSo() {
        // SPECS.md §4.7: opening an empty page would be worse than offering
        // nothing, but so would a click with no effect.
        val clicked = mutableListOf<Long>()
        show(
            DiscoverUiState(
                articles = listOf(uiArticle(id = 7L, isOpenable = false)),
                phase = DiscoverPhase.EndOfFeed,
            ),
            onArticleClick = { clicked += it },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.NO_LINK, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.card(7L)).performClick()

        assertTrue(clicked.isEmpty())
    }

    // ----- Card sharing (SPECS.md §4.3) ---------------------------------------

    /**
     * Share-specific entry point rather than a widened [show]: only sharing
     * needs both card commands at once, and adding them to `show` would have
     * made it an eight-parameter function.
     */
    private fun showArticle(
        article: ArticleUiModel,
        onArticleClick: (Long) -> Unit = {},
        onArticleShare: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            DiscoverScreen(
                uiState = DiscoverUiState(articles = listOf(article), phase = DiscoverPhase.EndOfFeed),
                onLoadMore = {},
                onRetry = {},
                onArticleClick = onArticleClick,
                onArticleShare = onArticleShare,
            )
        }
    }

    @Test
    fun anArticleWithALinkCanBeShared() {
        val shared = mutableListOf<Long>()
        showArticle(uiArticle(id = 7L), onArticleShare = { shared += it })

        composeRule.onNodeWithTag(DiscoverTestTags.share(7L)).performClick()

        assertEquals(listOf(7L), shared)
    }

    @Test
    fun anArticleWithoutLinkCarriesNoShareButton() {
        // Sharing a title alone would send a pointless message: same rule as
        // opening (SPECS.md §4.7).
        showArticle(uiArticle(id = 7L, isOpenable = false))

        composeRule.onNodeWithTag(DiscoverTestTags.share(7L)).assertDoesNotExist()
    }

    @Test
    fun theShareButtonDoesNotOpenTheArticle() {
        // Both commands live on the same card, and the whole card is
        // clickable: without this guard, sharing would also open the browser.
        val clicked = mutableListOf<Long>()
        val shared = mutableListOf<Long>()
        showArticle(
            uiArticle(id = 7L),
            onArticleClick = { clicked += it },
            onArticleShare = { shared += it },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.share(7L)).performClick()

        assertEquals(listOf(7L), shared)
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun theShareButtonAnnouncesItselfAndIsLargeEnoughToTouch() {
        showArticle(uiArticle(id = 7L))

        composeRule.onNodeWithContentDescription("Partager l'article").assertExists()

        val bounds = composeRule.onNodeWithTag(DiscoverTestTags.share(7L)).getBoundsInRoot()

        assertTrue(bounds.width >= MIN_TOUCH_TARGET, "largeur ${bounds.width}")
        assertTrue(bounds.height >= MIN_TOUCH_TARGET, "hauteur ${bounds.height}")
    }

    // ----- Feed states --------------------------------------------------------

    @Test
    fun theFirstLoadShowsAProgressIndicatorRatherThanAnEmptyList() {
        show(DiscoverUiState(phase = DiscoverPhase.InitialLoading))

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun anEmptyFeedExplainsItselfInsteadOfShowingNothing() {
        show(DiscoverUiState(phase = DiscoverPhase.EndOfFeed))

        composeRule.onNodeWithTag(DiscoverTestTags.EMPTY).assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.END_OF_FEED).assertDoesNotExist()
    }

    @Test
    fun theEndOfTheFeedIsStated() {
        // A list that simply stops growing is indistinguishable from a
        // breakdown (SPECS.md §4.4).
        show(DiscoverUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.EndOfFeed))

        composeRule.onNodeWithTag(DiscoverTestTags.END_OF_FEED).assertExists()
    }

    @Test
    fun theNextPageBeingLoadedIsVisibleUnderTheArticles() {
        show(DiscoverUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.LoadingMore))

        composeRule.onNodeWithText("Un titre").assertExists()
        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertExists()
    }

    @Test
    fun aFailedNextPageKeepsTheArticlesAndOffersARetry() {
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork),
            ),
        )

        composeRule.onNodeWithText("Un titre").assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.FAILURE).assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.RETRY).assertExists()
    }

    @Test
    fun retryingReachesTheCaller() {
        var retried = 0
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable),
            ),
            onRetry = { retried++ },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.RETRY).performClick()

        assertEquals(1, retried)
    }

    @Test
    fun eachFailureHasItsOwnMessage() {
        show(DiscoverUiState(phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork)))

        composeRule.onNodeWithText("Aucune connexion réseau.").assertExists()
    }

    @Test
    fun anEndedSessionShowsNoErrorMessage() {
        // The session disappears and the root gate switches on its own.
        show(DiscoverUiState(phase = DiscoverPhase.SessionEnded))

        composeRule.onNodeWithTag(DiscoverTestTags.FAILURE).assertDoesNotExist()
        composeRule.onNodeWithTag(DiscoverTestTags.EMPTY).assertDoesNotExist()
    }

    // ----- Offline (SPECS.md §5.2) --------------------------------------------

    @Test
    fun beingOfflineOverSomeContentShowsABannerAndNotAFullScreenError() {
        // A full-screen error over a usable cache would suggest an empty
        // application.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork),
                isOffline = true,
            ),
        )

        composeRule.onNodeWithTag(DiscoverTestTags.OFFLINE_BANNER).assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.LIST).assertExists()
        composeRule.onNodeWithText("Un titre").assertExists()
    }

    @Test
    fun theOfflineBannerReplacesTheErrorBlockButNotItsRetry() {
        // Two messages for a single cause, one of them in red, would make an
        // alarm where the banner suffices (SPECS.md §5.2).
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork),
                isOffline = true,
            ),
        )

        composeRule.onNodeWithTag(DiscoverTestTags.FAILURE).assertDoesNotExist()
        composeRule.onNodeWithText("Aucune connexion réseau.").assertDoesNotExist()
        composeRule.onNodeWithTag(DiscoverTestTags.RETRY).assertExists()
    }

    @Test
    fun beingOfflineWithoutContentShowsTheMessageAloneWithoutTheBanner() {
        show(DiscoverUiState(phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork), isOffline = true))

        composeRule.onNodeWithTag(DiscoverTestTags.OFFLINE_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(DiscoverTestTags.FAILURE).assertExists()
    }

    @Test
    fun theOfflineBannerSaysTheStateWithoutSoundingLikeABreakdown() {
        show(DiscoverUiState(articles = listOf(uiArticle()), isOffline = true, phase = DiscoverPhase.Idle))

        composeRule.onNodeWithText("Hors ligne", substring = true).assertExists()
    }

    @Test
    fun aRefusedOpeningIsExplainedAndAcknowledged() {
        var dismissed = 0
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Idle,
                isOffline = true,
                isOfflineOpenNoticeVisible = true,
            ),
            onOfflineNoticeDismiss = { dismissed++ },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.OFFLINE_NOTICE).assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.OFFLINE_NOTICE_DISMISS).performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun nothingIsSaidAboutOpeningWhenNothingHasBeenRefused() {
        show(DiscoverUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.Idle))

        composeRule.onNodeWithTag(DiscoverTestTags.OFFLINE_NOTICE).assertDoesNotExist()
    }

    // ----- Launch requests nothing (SPECS.md §5.1) ----------------------------

    @Test
    fun aShortFeedDoesNotLoadMoreWithoutAScroll() {
        // The cache, filtered of its read articles, sometimes fits entirely on
        // screen: the bottom is then reached without anyone moving a finger.
        // Without this guard, the request removed from launch came back through
        // infinite scrolling; observed on device, the last server contact date
        // still changed on every open.
        var loads = 0
        show(
            DiscoverUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.Idle),
            onLoadMore = { loads++ },
        )

        composeRule.waitForIdle()

        assertEquals(0, loads)
    }

    @Test
    fun scrollingAShortFeedDoesLoadMore() {
        // Scrolling is an action: pagination resumes.
        var loads = 0
        show(
            DiscoverUiState(
                articles = List(12) { uiArticle(id = it.toLong()) },
                phase = DiscoverPhase.Idle,
            ),
            onLoadMore = { loads++ },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.LIST).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertTrue(loads > 0, "la pagination doit suivre le défilement")
    }

    // ----- Stale feed (SPECS.md §4.6) -----------------------------------------

    @Test
    fun anOldFeedInvitesToReloadIt() {
        show(staleState())

        composeRule.onNodeWithTag(DiscoverTestTags.STALE_NOTICE).assertExists()
        composeRule.onNodeWithText("Ce flux date de plusieurs heures.").assertExists()
    }

    @Test
    fun aFeedThatIsNotOldSaysNothing() {
        show(DiscoverUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.Idle))

        composeRule.onNodeWithTag(DiscoverTestTags.STALE_NOTICE).assertDoesNotExist()
    }

    @Test
    fun theInvitationBorrowsTheExistingReload() {
        var refreshed = 0
        show(staleState(), onRefresh = { refreshed++ })

        composeRule.onNodeWithTag(DiscoverTestTags.STALE_NOTICE_REFRESH).performClick()

        assertEquals(1, refreshed)
    }

    @Test
    fun theInvitationCanBeSilencedWithoutReloading() {
        var silenced = 0
        var refreshed = 0
        show(staleState(), onRefresh = { refreshed++ }, onStaleNoticeDismiss = { silenced++ })

        composeRule.onNodeWithTag(DiscoverTestTags.STALE_NOTICE_DISMISS).performClick()

        assertEquals(1, silenced)
        assertEquals(0, refreshed)
    }

    @Test
    fun offlineOnlyOneStripOccupiesTheBottomOfTheScreen() {
        // Offline, the banner already explains why the feed is stale, and
        // "Reload" would only open an empty door.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Idle,
                isOffline = true,
                isOfflineOpenNoticeVisible = true,
                isStaleNoticeAvailable = true,
            ),
        )

        composeRule.onNodeWithTag(DiscoverTestTags.OFFLINE_NOTICE).assertExists()
        composeRule.onNodeWithTag(DiscoverTestTags.STALE_NOTICE).assertDoesNotExist()
    }

    /** A stale feed with something to read: the invitation is due there. */
    private fun staleState() = DiscoverUiState(
        articles = listOf(uiArticle()),
        phase = DiscoverPhase.Idle,
        isStaleNoticeAvailable = true,
    )

    // ----- Refresh (SPECS.md §4.6) --------------------------------------------

    @Test
    fun pullingTheFeedDownAsksForARefresh() {
        var refreshed = 0
        show(
            DiscoverUiState(articles = List(10) { uiArticle(id = it.toLong()) }, phase = DiscoverPhase.Idle),
            onRefresh = { refreshed++ },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.LIST).performTouchInput {
            swipeDown(startY = centerY, endY = bottom)
        }
        composeRule.waitForIdle()

        assertEquals(1, refreshed)
    }

    /**
     * A reader who has read everything has no error to retry, only an empty
     * screen, and pulling down is what they try there first (GOAL-025).
     */
    @Test
    fun anEmptyFeedCanBePulledDownToo() {
        var refreshed = 0
        show(DiscoverUiState(phase = DiscoverPhase.EndOfFeed), onRefresh = { refreshed++ })

        composeRule.onNodeWithTag(DiscoverTestTags.PULLABLE_MESSAGE).performTouchInput {
            swipeDown(startY = centerY, endY = bottom)
        }
        composeRule.waitForIdle()

        assertEquals(1, refreshed)
    }

    /** Failure without articles too: "Retry" remains, the gesture adds to it. */
    @Test
    fun aFailureWithNoArticleCanBePulledDown() {
        var refreshed = 0
        show(
            DiscoverUiState(phase = DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable)),
            onRefresh = { refreshed++ },
        )

        composeRule.onNodeWithTag(DiscoverTestTags.PULLABLE_MESSAGE).performTouchInput {
            swipeDown(startY = centerY, endY = bottom)
        }
        composeRule.waitForIdle()

        assertEquals(1, refreshed)
    }

    /**
     * The first load is not pullable: its request is already in flight, and a
     * second start would deliver nothing sooner.
     */
    @Test
    fun theFirstLoadOffersNoPull() {
        show(DiscoverUiState(phase = DiscoverPhase.InitialLoading))

        composeRule.onNodeWithTag(DiscoverTestTags.PULLABLE_MESSAGE).assertDoesNotExist()
    }

    @Test
    fun aRefreshInProgressLeavesTheArticlesInPlace() {
        // The refresh happens over the feed: it does not replace it with an
        // indicator, otherwise the reading position would be lost.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Idle,
                isRefreshing = true,
            ),
        )

        composeRule.onNodeWithText("Un titre").assertExists()
    }

    // ----- Anticipated loading ------------------------------------------------

    @Test
    fun theNextPageIsRequestedBeforeTheBottomIsReached() {
        var requested = 0
        val articles = List(40) { uiArticle(id = it.toLong()) }
        show(
            DiscoverUiState(articles = articles, phase = DiscoverPhase.Idle),
            onLoadMore = { requested++ },
        )

        // The list is not scrolled to the very end: the threshold is crossed a
        // few articles earlier.
        composeRule.onNodeWithTag(DiscoverTestTags.LIST).performScrollToIndex(articles.size - 3)
        composeRule.waitForIdle()

        assertTrue(requested > 0)
    }

    @Test
    fun noPageIsRequestedWhileTheBottomIsStillFarAway() {
        var requested = 0
        show(
            DiscoverUiState(articles = List(40) { uiArticle(id = it.toLong()) }, phase = DiscoverPhase.Idle),
            onLoadMore = { requested++ },
        )

        composeRule.waitForIdle()

        assertEquals(0, requested)
    }
}

/** Tolerated deviation on an aspect ratio measured in pixel-rounded dp. */
private const val RATIO_TOLERANCE = 0.05f

/** Minimum touch target required by SPECS.md §7.1. */
private val MIN_TOUCH_TARGET = 48.dp

private fun uiArticle(
    id: Long = 1L,
    imageUrl: String? = null,
    hasIllustration: Boolean = imageUrl != null,
    isOpenable: Boolean = true,
): ArticleUiModel = ArticleUiModel(
    id = id,
    title = "Un titre",
    feedTitle = "Le Monde",
    publishedAt = RelativeTime.Hours(2),
    excerpt = "Un extrait.",
    imageUrl = imageUrl,
    hasIllustration = hasIllustration,
    isOpenable = isOpenable,
)
