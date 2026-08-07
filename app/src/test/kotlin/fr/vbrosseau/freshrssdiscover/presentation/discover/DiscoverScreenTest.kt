package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import fr.vbrosseau.freshrssdiscover.presentation.LoadingTestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
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
    ) {
        composeRule.setContent {
            DiscoverScreen(
                uiState = uiState,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onArticleClick = onArticleClick,
                onRefresh = onRefresh,
                onOfflineNoticeDismiss = onOfflineNoticeDismiss,
            )
        }
    }

    // ----- Présentation d'un article ------------------------------------------

    @Test
    fun anArticleShowsItsTitleItsSourceAndItsAge() {
        // Sans le nom du flux, le mélange des sources serait déroutant.
        show(DiscoverUiState(articles = listOf(uiArticle()), phase = DiscoverPhase.EndOfFeed))

        composeRule.onNodeWithText("Un titre").assertExists()
        composeRule.onNodeWithText("Le Monde", substring = true).assertExists()
        composeRule.onNodeWithText("il y a 2 h", substring = true).assertExists()
        composeRule.onNodeWithText("Un extrait.").assertExists()
    }

    @Test
    fun anArticleWithoutIllustrationLeavesNoEmptySpace() {
        // SPECS.md §4.3 : ni cadre vide, ni image de remplacement générique.
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
        // Une image qu'on ne peut pas obtenir ne se distingue en rien, pour le
        // lecteur, d'un article qui n'en a pas : le créneau se referme
        // (SPECS.md §4.3).
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
        // Cas dégradé : le flux annonce une image sans en donner l'adresse. Il
        // n'y a rien à charger, donc rien à réserver.
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
        // L'image factice est carrée : si la hauteur du créneau en dépendait,
        // le rapport mesuré vaudrait 1. Le voir tenir à 16/9 prouve que la
        // liste ne se décalera pas au moment où l'image arrive.
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
        // SPECS.md §7.1 laisse le choix entre une description et un marquage
        // décoratif : c'est le second qui est retenu, et l'absence de
        // description sémantique est ce qui le matérialise.
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
        // SPECS.md §4.7 : ouvrir une page vide serait pire que ne rien
        // proposer, mais un clic sans effet le serait tout autant.
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

    // ----- États du flux ------------------------------------------------------

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
        // Une liste qui cesse simplement de s'allonger est indistinguable
        // d'une panne (SPECS.md §4.4).
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
        // La session disparaît et l'aiguillage racine bascule tout seul.
        show(DiscoverUiState(phase = DiscoverPhase.SessionEnded))

        composeRule.onNodeWithTag(DiscoverTestTags.FAILURE).assertDoesNotExist()
        composeRule.onNodeWithTag(DiscoverTestTags.EMPTY).assertDoesNotExist()
    }

    // ----- Hors ligne (SPECS.md §5.2) -----------------------------------------

    @Test
    fun beingOfflineOverSomeContentShowsABannerAndNotAFullScreenError() {
        // Un écran d'erreur plein cadre par-dessus un cache utilisable ferait
        // croire à une application vide.
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
        // Deux messages pour une seule cause, dont un en rouge, feraient une
        // alarme là où le bandeau suffit (SPECS.md §5.2).
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

    // ----- Rafraîchissement (SPECS.md §4.6) -----------------------------------

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

    @Test
    fun aRefreshInProgressLeavesTheArticlesInPlace() {
        // Le rafraîchissement se fait **par-dessus** le flux : il ne le
        // remplace pas par un indicateur, sinon la lecture serait perdue.
        show(
            DiscoverUiState(
                articles = listOf(uiArticle()),
                phase = DiscoverPhase.Idle,
                isRefreshing = true,
            ),
        )

        composeRule.onNodeWithText("Un titre").assertExists()
    }

    // ----- Chargement anticipé ------------------------------------------------

    @Test
    fun theNextPageIsRequestedBeforeTheBottomIsReached() {
        var requested = 0
        val articles = List(40) { uiArticle(id = it.toLong()) }
        show(
            DiscoverUiState(articles = articles, phase = DiscoverPhase.Idle),
            onLoadMore = { requested++ },
        )

        // La liste n'est pas défilée jusqu'au bout : le seuil se franchit
        // quelques articles avant.
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

/** Écart toléré sur un rapport d'aspect mesuré en dp arrondis au pixel. */
private const val RATIO_TOLERANCE = 0.05f

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
