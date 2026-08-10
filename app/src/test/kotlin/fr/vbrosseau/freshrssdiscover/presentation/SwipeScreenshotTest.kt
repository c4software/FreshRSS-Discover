package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.LOADABLE_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import fr.vbrosseau.freshrssdiscover.presentation.discover.TINY_IMAGE_URL
import fr.vbrosseau.freshrssdiscover.presentation.discover.installFakeImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.discover.resetImageLoader
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeScreen
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeUiState
import fr.vbrosseau.freshrssdiscover.presentation.swipe.pageCount
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Visual references for the Swipe view (SPECS.md §4.8, GOAL-012-T08).
 *
 * Three non-interchangeable situations:
 *
 * - an illustrated article, where the contrast of the banner against the
 *   following text is at stake;
 * - an article without illustration, which must fill the screen without
 *   leaving a hole at the top (SPECS.md §4.3);
 * - the end of the feed, which must be readable: a swipe that stops
 *   responding is indistinguishable from a failure (SPECS.md §4.4).
 *
 * The navigation bar appears on all three: it is the alternative to the
 * gesture (GOAL-012-T07), and its disabled button is exactly the kind of
 * element a textual assertion does not judge; this repo has already shipped
 * an invisible indicator on a disabled button.
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
     * Without an illustration, the screen starts with the text.
     *
     * Also the capture showing the long excerpt chosen for this mode
     * (SPECS.md §8, question 8): it must fill the screen, not overflow it by
     * ten pages.
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
     * The end of the feed, reached by one more swipe.
     *
     * The "Next" button is disabled there: nothing comes after. This is where
     * a disabled label is checked for readability in both themes, so it does
     * not vanish into the background.
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
     * The stale-feed reload notice, over a full-screen card.
     *
     * Two situations, not one: in List mode the strip sits on a list
     * background, here on an illustration. Contrast is therefore judged in a
     * different place, and this repo has already shipped an invisible
     * indicator on a badly chosen background. Also checks that it does not
     * cover the share button, the only control of this mode since the whole
     * card opens the article.
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

    /**
     * An article without a usable link (SPECS.md §4.7).
     *
     * The only capture showing what Swipe mode becomes when there is nothing
     * to do: no share button, and the note that replaces every control.
     * Without it, no image would attest that removing the open button did not
     * take that explanation with it.
     */
    @Test
    fun anArticleWithoutAnyLinkFullScreen() {
        capture("balayage-article-sans-lien") {
            swipe(
                SwipeUiState(
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
     * The same comparison full screen, where the defect is glaring: the slot
     * occupies half the height (SPECS.md §4.3).
     */
    @Test
    fun aTinyIllustrationFullScreenSitsOnItsBackdrop() {
        capture("balayage-illustration-minuscule") {
            swipe(
                SwipeUiState(
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

    @Composable
    private fun swipe(uiState: SwipeUiState, initialPage: Int = 0) {
        SwipeScreen(
            uiState = uiState,
            onLoadMore = {},
            onRetry = {},
            onArticleClick = {},
            onArticleShare = {},
            pagerState = rememberPagerState(initialPage = initialPage) { uiState.pageCount },
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
