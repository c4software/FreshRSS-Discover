package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.LOADABLE_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.installFakeImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.discover.resetImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeScreen
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeUiState
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Références visuelles de la vue Balayage (SPECS.md §4.8, GOAL-012-T08).
 *
 * Trois situations, et elles ne sont pas interchangeables :
 *
 * - **un article illustré**, où se joue le contraste du bandeau contre le texte
 *   qui le suit ;
 * - **un article sans illustration**, qui doit remplir l'écran sans laisser de
 *   trou en haut (SPECS.md §4.3) ;
 * - **la fin du flux**, qui doit se lire — un balayage qui cesse de répondre est
 *   indistinguable d'une panne (SPECS.md §4.4).
 *
 * La barre de navigation figure sur les trois : c'est l'alternative au geste
 * (GOAL-012-T07), et son bouton désactivé est précisément le genre d'élément
 * qu'une assertion textuelle ne juge pas — ce dépôt a déjà livré un indicateur
 * invisible sur bouton désactivé.
 */
class SwipeScreenshotTest : ScreenshotTest() {

    @Before
    fun installImageLoader() = installFakeImageLoader()

    @After
    fun restoreImageLoader() = resetImageLoader()

    @Test
    fun anIllustratedArticleFullScreen() {
        capture("balayage-article-illustre") {
            swipe(
                SwipeUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "Le télescope spatial livre ses premières images de la nébuleuse",
                            imageUrl = LOADABLE_IMAGE_URL,
                        ),
                        sampleArticle(id = 2L, title = "L'article suivant, hors de l'écran"),
                    ),
                    phase = DiscoverPhase.Idle,
                ),
            )
        }
    }

    /**
     * Sans illustration, l'écran commence par le texte.
     *
     * C'est aussi la capture qui montre l'extrait long retenu pour ce mode
     * (SPECS.md §8, question 8) : il doit remplir l'écran, pas le déborder de
     * dix pages.
     */
    @Test
    fun anArticleWithoutIllustrationFullScreen() {
        capture("balayage-article-sans-illustration") {
            swipe(
                SwipeUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "Un article sans illustration, qui n'en laisse pas la place vide",
                            excerpt = LONG_EXCERPT,
                        ),
                        sampleArticle(id = 2L, title = "L'article suivant, hors de l'écran"),
                    ),
                    phase = DiscoverPhase.Idle,
                ),
            )
        }
    }

    /**
     * La fin du flux, atteinte d'un balayage de plus.
     *
     * Le bouton « Suivant » y est désactivé : il n'y a plus rien après. C'est
     * là qu'on vérifie qu'un libellé indisponible reste **lisible** dans les
     * deux thèmes, et ne disparaît pas dans le fond.
     */
    @Test
    fun theEndOfTheFeed() {
        capture("balayage-fin-de-flux") {
            swipe(
                SwipeUiState(
                    articles = listOf(sampleArticle(id = 1L, title = "Le dernier article du flux")),
                    phase = DiscoverPhase.EndOfFeed,
                ),
                initialPage = 1,
            )
        }
    }

    /**
     * L'invitation à recharger un flux ancien, posée sur une carte plein écran.
     *
     * Deux situations et pas une : en mode Liste la bandelette repose sur un
     * fond de liste, ici sur une **illustration**. Le contraste ne se juge donc
     * pas au même endroit, et ce dépôt a déjà livré un indicateur invisible sur
     * un fond mal choisi. On y regarde aussi qu'elle ne recouvre pas la
     * commande d'ouverture de l'article.
     */
    @Test
    fun anOldFeedInvitingToReload() {
        capture("balayage-flux-ancien") {
            swipe(
                SwipeUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "Le télescope spatial livre ses premières images de la nébuleuse",
                            imageUrl = LOADABLE_IMAGE_URL,
                        ),
                    ),
                    phase = DiscoverPhase.Idle,
                    isStaleNoticeAvailable = true,
                ),
            )
        }
    }

    @Composable
    private fun swipe(uiState: SwipeUiState, initialPage: Int = 0) {
        SwipeScreen(
            uiState = uiState,
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            pagerState = rememberPagerState(initialPage = initialPage) { uiState.pageCount },
        )
    }
}

/**
 * Un extrait à la longueur retenue pour ce mode (SPECS.md §8, question 8).
 *
 * Volontairement au plafond : c'est la seule façon de vérifier sur une image
 * que 1 400 caractères remplissent l'écran sans le déborder de plusieurs pages.
 */
private const val LONG_EXCERPT =
    "Après six mois de calibrage, l'instrument a transmis une série de clichés d'une précision inédite, " +
        "que les astronomes analysent depuis lundi. Les premières mesures confirment la présence de " +
        "vapeur d'eau dans les couches externes du nuage, à une distance que les modèles précédents " +
        "jugeaient improbable. L'équipe attend désormais la deuxième campagne d'observation, prévue " +
        "pour la fin du trimestre, avant de publier ses conclusions. Les données brutes seront mises " +
        "à disposition de la communauté dès leur validation, selon un calendrier que l'agence a " +
        "confirmé la semaine dernière. Plusieurs laboratoires européens ont déjà annoncé qu'ils " +
        "consacreraient une partie de leur temps de calcul à leur traitement, dans l'espoir d'affiner " +
        "les modèles de formation stellaire qui prévalent depuis une vingtaine d'années. Le débat " +
        "reste ouvert sur l'origine du rayonnement observé en bordure du nuage, que deux équipes " +
        "interprètent de façons opposées. La prudence domine néanmoins les commentaires : un seul " +
        "jeu de mesures, aussi précis soit-il, ne suffit pas à trancher une question posée depuis " +
        "les premières observations au sol."

private fun sampleArticle(
    id: Long,
    title: String,
    imageUrl: String? = null,
    excerpt: String = "Un extrait de l'article, écourté par l'application avant l'affichage.",
): ArticleUiModel = ArticleUiModel(
    id = id,
    title = title,
    feedTitle = "Le Monde — Sciences",
    publishedAt = RelativeTime.Hours(2),
    excerpt = excerpt,
    imageUrl = imageUrl,
    isOpenable = true,
)
