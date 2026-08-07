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
 * Références visuelles de l'ossature.
 *
 * Les écrans réels viendront s'ajouter ici au fil des Goals. Capturer dès
 * maintenant la barre de navigation et l'écran d'attente sert d'abord à
 * éprouver la chaîne Roborazzi elle-même : sans une référence enregistrée,
 * `verifyRoborazziDebug` ne compare rien et passerait à tort.
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
     * Le message le plus long de l'écran, sur l'avertissement le plus long.
     *
     * C'est le cas qui déborde : si une mise en page casse, c'est ici que cela
     * se voit — et une assertion textuelle ne le montrerait pas.
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

    // ----- Flux Discover ------------------------------------------------------

    /**
     * Le flux ordinaire, avec et sans illustration, et un article sans lien.
     *
     * Les trois cas cohabitent volontairement sur une même image : c'est leur
     * juxtaposition qui montre qu'un article sans illustration ne laisse pas de
     * trou dans la colonne (SPECS.md §4.3).
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
     * Un échec de page suivante, sous les articles déjà affichés.
     *
     * C'est le cas que SPECS.md §4.4 protège : la capture doit montrer la liste
     * intacte et le message en pied, jamais un écran d'erreur plein.
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
