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
 * Langue figée au français, comme le harnais de capture (ARCHITECTURE.md §8.2).
 *
 * Ces cas affirment des libellés **littéraux**, et l'interface est bilingue
 * depuis GOAL-021-T02 : le français vit dans `values-fr/`, l'anglais dans
 * `values/`. Sans ce fanion, Robolectric rend la langue par défaut — l'anglais
 * — et chaque assertion tombe. Ce n'est pas une commodité de test : c'est la
 * même décision que pour les captures, dont les références sont françaises.
 *
 * Ce que `values/` contient est éprouvé ailleurs, par un cas dédié en `en-rUS`
 * (`EnglishStringsTest`) : sans lui, une chaîne oubliée à la traduction ne se
 * verrait que sur un appareil anglophone.
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

    // ----- Partage de la carte (SPECS.md §4.3) --------------------------------

    /**
     * Point d'entrée propre au partage plutôt qu'un [show] élargi : lui seul a
     * besoin des **deux** commandes de la carte à la fois, et les ajouter à
     * `show` en aurait fait une fonction à huit paramètres.
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
        // Partager un titre seul enverrait un message sans objet : même règle
        // que l'ouverture (SPECS.md §4.7).
        showArticle(uiArticle(id = 7L, isOpenable = false))

        composeRule.onNodeWithTag(DiscoverTestTags.share(7L)).assertDoesNotExist()
    }

    @Test
    fun theShareButtonDoesNotOpenTheArticle() {
        // Les deux commandes vivent sur la même carte, et la carte entière est
        // cliquable : sans cette garde, partager ouvrirait aussi le navigateur.
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

    // ----- Le lancement ne demande rien (SPECS.md §5.1) -----------------------

    @Test
    fun aShortFeedDoesNotLoadMoreWithoutAScroll() {
        // Le cache filtré de ses articles lus tient parfois entièrement à
        // l'écran : le bas est alors atteint sans que personne n'ait bougé le
        // doigt. Sans cette garde, la requête retirée du lancement revenait
        // par le défilement infini — constaté sur appareil, la date du dernier
        // contact serveur changeait encore à chaque ouverture.
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
        // Défiler est une action : la pagination reprend ses droits.
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

    // ----- Flux ancien (SPECS.md §4.6) ----------------------------------------

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
        // Hors ligne, le bandeau explique déjà pourquoi le flux est ancien, et
        // « Recharger » n'ouvrirait qu'une porte vide.
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

    /** Un flux ancien, avec de quoi lire : l'invitation y est due. */
    private fun staleState() = DiscoverUiState(
        articles = listOf(uiArticle()),
        phase = DiscoverPhase.Idle,
        isStaleNoticeAvailable = true,
    )

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

    /**
     * Le cul-de-sac de GOAL-025 : un lecteur qui a tout lu n'a pas d'erreur à
     * reprendre, seulement un écran vide, et c'est le tirage qu'il y tente
     * d'abord. Signalé par l'auteur.
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

    /** L'échec sans article aussi : « Réessayer » demeure, le geste s'y ajoute. */
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
     * Le premier chargement n'est pas tirable : sa requête est déjà en vol, et
     * un second départ ne rendrait rien de plus tôt.
     */
    @Test
    fun theFirstLoadOffersNoPull() {
        show(DiscoverUiState(phase = DiscoverPhase.InitialLoading))

        composeRule.onNodeWithTag(DiscoverTestTags.PULLABLE_MESSAGE).assertDoesNotExist()
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

/** Cible tactile minimale exigée par SPECS.md §7.1. */
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
