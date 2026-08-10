package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.runtime.Composable
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverScreen
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverUiState
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import org.junit.Test

/**
 * Visual references for the feed's network states.
 *
 * Kept apart from the ordinary screen captures because none of these is a
 * screen: they are overlays (a banner above the feed, a strip above
 * everything, an indicator pulled from the top). What is examined is their
 * coexistence with the content underneath, and their contrast in both themes.
 */
class DiscoverFeedScreenshotTest : ScreenshotTest() {

    /**
     * A refresh in progress, over the articles.
     *
     * Shows that the gesture replaces nothing: the list is intact under the
     * indicator (SPECS.md §4.6).
     */
    @Test
    fun feedBeingRefreshed() {
        capture("discover-rafraichissement") {
            discover(
                DiscoverUiState(
                    articles = sampleArticles(),
                    phase = DiscoverPhase.Idle,
                    isRefreshing = true,
                ),
            )
        }
    }

    /**
     * The offline banner above a usable cache.
     *
     * Two things to verify: the content stays fully readable (a full-frame
     * error screen would be the defect), and the banner reads in both themes
     * without turning into an alarm.
     */
    @Test
    fun offlineBannerOverCachedArticles() {
        capture("discover-hors-ligne") {
            discover(
                DiscoverUiState(
                    articles = sampleArticles(),
                    phase = DiscoverPhase.Failed(DiscoverFailure.NoNetwork),
                    isOffline = true,
                ),
            )
        }
    }

    /**
     * The opening refused for lack of network (SPECS.md §5.2).
     *
     * Captured for its contrast: the strip paints an inverted surface, where
     * an action label left at its default color would go unnoticed, the kind
     * of defect no textual assertion reveals.
     */
    @Test
    fun refusedOpeningWhileOffline() {
        capture("discover-ouverture-hors-ligne") {
            discover(
                DiscoverUiState(
                    articles = sampleArticles(),
                    phase = DiscoverPhase.Idle,
                    isOffline = true,
                    isOfflineOpenNoticeVisible = true,
                ),
            )
        }
    }

    /**
     * The invitation to reload a stale feed (SPECS.md §4.6).
     *
     * Captured because it carries two commands where the offline notice
     * carried one: what is examined is that they fit side by side without the
     * message wrapping onto three lines, and that the second stands apart from
     * the first without looking like an ornament.
     */
    @Test
    fun anOldFeedInvitingToReload() {
        capture("discover-flux-ancien") {
            discover(
                DiscoverUiState(
                    articles = sampleArticles(),
                    phase = DiscoverPhase.Idle,
                    isStaleNoticeAvailable = true,
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

    /**
     * A short article without an illustration: the only case where the share
     * button's alignment is visible.
     *
     * A guard, not one more state. The card's inner column hugged its content,
     * so the share `align(End)` aligned on the text width rather than the card
     * width. No existing capture could show it: their articles all have either
     * an illustration, which forces full width, or text long enough to reach
     * it. Observed on the emulator (GOAL-022-T03), on a Hacker News brief
     * whose excerpt fit in one word. The read-article badge was caught the
     * same way in `GOAL-017-T02`, so the capture stays.
     */
    @Test
    fun theShareButtonHugsTheCardEdgeOnAShortArticle() {
        capture("discover-article-court") {
            discover(
                DiscoverUiState(
                    articles = listOf(
                        ArticleUiModel(
                            id = 42L,
                            title = "Une brève",
                            feedTitle = "Hacker News",
                            publishedAt = RelativeTime.Hours(1),
                            excerpt = "Commentaires",
                            isOpenable = true,
                        ),
                    ),
                    phase = DiscoverPhase.Idle,
                ),
            )
        }
    }

    private fun sampleArticles(): List<ArticleUiModel> = listOf(
        ArticleUiModel(
            id = 1L,
            title = "Le télescope spatial livre ses premières images de la nébuleuse",
            feedTitle = "Le Monde — Sciences",
            publishedAt = RelativeTime.Hours(2),
            excerpt = "Après six mois de calibrage, l'instrument a transmis une série de clichés " +
                "d'une précision inédite, que les astronomes analysent depuis lundi.",
            isOpenable = true,
        ),
        ArticleUiModel(
            id = 2L,
            title = "Un billet enregistré la veille, toujours lisible sans réseau",
            feedTitle = "Carnet de bord",
            publishedAt = RelativeTime.Days(1),
            excerpt = "Le cache local garde les articles récupérés : hors ligne, le flux reste " +
                "consultable et rien n'est vidé.",
            isOpenable = true,
        ),
    )
}
