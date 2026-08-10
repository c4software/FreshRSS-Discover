package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverScreen
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverUiState
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginFailure
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginScreen
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginUiState
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppNavigationBar
import fr.vbrosseau.freshrssdiscover.presentation.navigation.AppRoutes
import org.junit.Test

/**
 * Visual references for the app skeleton.
 *
 * Capturing the navigation bar and the waiting screen also exercises the
 * Roborazzi chain itself: without a recorded reference,
 * `verifyRoborazziDebug` compares nothing and would pass wrongly.
 */
class ScreensScreenshotTest : ScreenshotTest() {

    @Test
    fun navigationBar() {
        capture("barre-navigation") {
            AppNavigationBar(currentRoute = AppRoutes.DISCOVER, onSelect = {})
        }
    }

    @Test
    fun loginScreenEmpty() {
        capture("connexion-vide") { login(LoginUiState()) }
    }

    @Test
    fun loginScreenFilled() {
        capture("connexion-remplie") {
            login(
                LoginUiState(
                    serverAddress = "rss.exemple.org",
                    username = "alice",
                    apiPassword = "mot-de-passe-api",
                    canSubmit = true,
                ),
            )
        }
    }

    /**
     * The longest message of the screen, over the longest warning.
     *
     * This is the overflow case: if a layout breaks, it shows here, and a
     * textual assertion would not reveal it.
     */
    @Test
    fun loginScreenWithTheLongestFailure() {
        capture("connexion-erreur") {
            login(
                LoginUiState(
                    serverAddress = "http://rss.exemple.org",
                    username = "alice",
                    showsInsecureWarning = true,
                    failure = LoginFailure.Server(AuthError.ApiDisabled),
                ),
            )
        }
    }

    @Test
    fun loginScreenConnecting() {
        capture("connexion-en-cours") {
            login(
                LoginUiState(
                    serverAddress = "rss.exemple.org",
                    username = "alice",
                    apiPassword = "mot-de-passe-api",
                    isSubmitting = true,
                ),
            )
        }
    }

    // ----- Discover feed ------------------------------------------------------

    /**
     * The ordinary feed, with and without illustration, plus an article
     * without a link.
     *
     * The three cases share one image on purpose: their juxtaposition shows
     * that an article without an illustration leaves no hole in the column
     * (SPECS.md §4.3).
     */
    @Test
    fun discoverScreenLoaded() {
        capture("discover-flux") {
            discover(DiscoverUiState(articles = sampleArticles(), phase = DiscoverPhase.Idle))
        }
    }

    @Test
    fun discoverScreenEmpty() {
        capture("discover-vide") {
            discover(DiscoverUiState(phase = DiscoverPhase.EndOfFeed))
        }
    }

    @Test
    fun discoverScreenFirstLoad() {
        capture("discover-chargement") {
            discover(DiscoverUiState(phase = DiscoverPhase.InitialLoading))
        }
    }

    /**
     * A next-page failure, below the articles already displayed.
     *
     * The case SPECS.md §4.4 protects: the capture must show the list intact
     * and the message in the footer, never a full error screen.
     */
    @Test
    fun discoverScreenWithAFailedNextPage() {
        capture("discover-erreur") {
            discover(
                DiscoverUiState(
                    articles = sampleArticles(),
                    phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork),
                ),
            )
        }
    }

    @Test
    fun discoverScreenAtTheEndOfTheFeed() {
        capture("discover-fin") {
            discover(
                DiscoverUiState(
                    articles = sampleArticles().takeLast(1),
                    phase = DiscoverPhase.EndOfFeed,
                ),
            )
        }
    }

    @Composable
    private fun discover(uiState: DiscoverUiState) {
        DiscoverScreen(
            uiState = uiState,
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
        )
    }

    private fun sampleArticles(): List<ArticleUiModel> = listOf(
        ArticleUiModel(
            id = 1L,
            title = "Le télescope spatial livre ses premières images de la nébuleuse",
            feedTitle = "Le Monde — Sciences",
            publishedAt = RelativeTime.Hours(2),
            excerpt = "Après six mois de calibrage, l'instrument a transmis une série de clichés " +
                "d'une précision inédite, que les astronomes analysent depuis lundi.",
            hasIllustration = true,
            isOpenable = true,
        ),
        ArticleUiModel(
            id = 2L,
            title = "Un billet sans illustration",
            feedTitle = "Carnet de bord",
            publishedAt = RelativeTime.Days(3),
            excerpt = "L'absence d'image ne doit produire ni cadre vide ni image de remplacement : " +
                "la carte se referme simplement sur son texte.",
            hasIllustration = false,
            isOpenable = true,
        ),
        ArticleUiModel(
            id = 3L,
            title = "Une brève dont le flux n'a pas fourni de lien",
            feedTitle = "Dépêches",
            publishedAt = RelativeTime.Minutes(12),
            excerpt = "Sans lien exploitable, la carte n'est pas cliquable — et le dit.",
            hasIllustration = false,
            isOpenable = false,
        ),
    )

    @Composable
    private fun login(uiState: LoginUiState) {
        LoginScreen(
            uiState = uiState,
            onServerAddressChange = {},
            onUsernameChange = {},
            onApiPasswordChange = {},
            onSubmit = {},
        )
    }
}
