package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.LOADABLE_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.SMALL_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.TINY_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.installFakeImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.discover.resetImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.immersive.ImmersiveScreen
import fr.vbrosseau.freshrssdiscover.presentation.immersive.ImmersiveUiState
import fr.vbrosseau.freshrssdiscover.presentation.immersive.pageCount
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Visual references for the Immersive view (SPECS.md §4.8, GOAL-038-T02).
 *
 * Situations that are not interchangeable:
 *
 * - an illustrated article, where the text sits on the scrim over the
 *   picture: contrast is judged there, in both themes;
 * - an article without illustration, which stands on the tinted backdrop
 *   rather than leaving the top of the page empty (SPECS.md §4.3);
 * - the end of the feed, which must be readable: a flick that stops
 *   responding is indistinguishable from a failure (SPECS.md §4.4).
 */
class ImmersiveScreenshotTest : ScreenshotTest() {

    @Before
    fun installImageLoader() = installFakeImageLoader()

    @After
    fun restoreImageLoader() = resetImageLoader()

    @Test
    fun anIllustratedArticleFullScreen() {
        capture("immersif-article-illustre") {
            immersive(
                ImmersiveUiState(
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
     * The framed look, for a picture too small to go full screen: set down
     * on the page — inset, rounded, shadowed — over a dimmed blur of itself.
     */
    @Test
    fun aFramedPictureOnItsDimmedBlur() {
        capture("immersif-article-cadre") {
            immersive(
                ImmersiveUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "Une photographie posée droite sur la page",
                            imageUrl = SMALL_IMAGE_URL,
                        ),
                    ),
                    phase = DiscoverPhase.Idle,
                ),
            )
        }
    }

    /**
     * Without an illustration, the screen starts with the text.
     *
     * Also the capture showing a long excerpt: it must end in an ellipsis at
     * the bottom of the page, never run under it (SPECS.md §8, question 8).
     */
    @Test
    fun anArticleWithoutIllustrationFullScreen() {
        capture("immersif-article-sans-illustration") {
            immersive(
                ImmersiveUiState(
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
     * The end of the feed, reached by one more flick.
     */
    @Test
    fun theEndOfTheFeed() {
        capture("immersif-fin-de-flux") {
            immersive(
                ImmersiveUiState(
                    articles = listOf(sampleArticle(id = 1L, title = "Le dernier article du flux")),
                    phase = DiscoverPhase.EndOfFeed,
                ),
                initialPage = 1,
            )
        }
    }

    /**
     * The stale-feed reload notice, under a full-screen page.
     *
     * Checks that it takes its own room rather than covering the bottom of
     * the page, where the text and the share button live — the only control
     * of this mode since the whole page opens the article.
     */
    @Test
    fun anOldFeedInvitingToReload() {
        capture("immersif-flux-ancien") {
            immersive(
                ImmersiveUiState(
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

    /**
     * An article without a usable link (SPECS.md §4.7).
     *
     * The only capture showing what the page becomes when there is nothing
     * to do: no share button, and the note that replaces every control.
     * Without it, no image would attest that removing the open button did not
     * take that explanation with it.
     */
    @Test
    fun anArticleWithoutAnyLinkFullScreen() {
        capture("immersif-article-sans-lien") {
            immersive(
                ImmersiveUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "Une brève dont le flux n'a fourni aucun lien",
                            isOpenable = false,
                        ),
                    ),
                    phase = DiscoverPhase.EndOfFeed,
                ),
            )
        }
    }

    /**
     * A tiny picture cropped to the whole page: it is upscaled, which is the
     * price of a backdrop, and the scrim must still carry the text over it.
     */
    @Test
    fun aTinyIllustrationFullScreenSitsOnItsBackdrop() {
        capture("immersif-illustration-minuscule") {
            immersive(
                ImmersiveUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "Une illustration plus petite que le créneau",
                            imageUrl = TINY_IMAGE_URL,
                        ),
                    ),
                    phase = DiscoverPhase.Idle,
                ),
            )
        }
    }

    /**
     * The veil of a foreground reload (GOAL-041): opaque, so the article
     * underneath must not show through in either theme.
     */
    @Test
    fun aForegroundReloadVeilsTheArticle() {
        capture("immersif-rechargement-avant-plan") {
            immersive(
                ImmersiveUiState(
                    articles = listOf(sampleArticle(id = 1L, title = "L'article qu'il ne faut pas revoir")),
                    phase = DiscoverPhase.Idle,
                    isRefreshing = true,
                ),
                isReloadingOnForeground = true,
            )
        }
    }

    @Composable
    private fun immersive(
        uiState: ImmersiveUiState,
        initialPage: Int = 0,
        isReloadingOnForeground: Boolean = false,
    ) {
        ImmersiveScreen(
            uiState = uiState,
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
            pagerState = rememberPagerState(initialPage = initialPage) { uiState.pageCount },
            isReloadingOnForeground = isReloadingOnForeground,
        )
    }
}

/**
 * An excerpt at the length chosen for this mode (SPECS.md §8, question 8).
 *
 * Deliberately at the cap: the only way to verify on an image that 1,400
 * characters fill the screen without overflowing it by several pages.
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
    isOpenable: Boolean = true,
): ArticleUiModel = ArticleUiModel(
    id = id,
    title = title,
    feedTitle = "Le Monde — Sciences",
    publishedAt = RelativeTime.Hours(2),
    excerpt = excerpt,
    imageUrl = imageUrl,
    isOpenable = isOpenable,
)
