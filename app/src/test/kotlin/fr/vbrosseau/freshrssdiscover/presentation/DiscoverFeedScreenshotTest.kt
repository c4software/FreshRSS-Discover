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
 * Références visuelles des états réseau du flux.
 *
 * Ils vivent à part des captures d'écrans ordinaires parce qu'aucun n'est un
 * écran : ce sont des **surimpressions** — un bandeau au-dessus du flux, une
 * bandelette au-dessus de tout, un indicateur tiré depuis le haut. Ce qu'on y
 * regarde est justement leur cohabitation avec le contenu qui reste dessous, et
 * leur contraste dans les deux thèmes.
 */
class DiscoverFeedScreenshotTest : ScreenshotTest() {

    /**
     * Le rafraîchissement en cours, par-dessus les articles.
     *
     * C'est la capture qui montre que le geste ne remplace rien : la liste est
     * intacte sous l'indicateur (SPECS.md §4.6).
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
     * Le bandeau hors ligne au-dessus d'un cache utilisable.
     *
     * Deux choses à y vérifier : que le contenu reste entièrement lisible — un
     * écran d'erreur plein cadre serait le défaut — et que le bandeau se lit
     * dans les deux thèmes sans virer à l'alarme.
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
     * L'ouverture refusée faute de réseau (SPECS.md §5.2).
     *
     * Capturée pour son contraste : la bandelette peint une surface inversée,
     * là où un libellé d'action laissé à sa couleur par défaut passerait
     * inaperçu — c'est le genre de défaut qu'aucune assertion textuelle ne
     * révèle.
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
     * L'invitation à recharger un flux ancien (SPECS.md §4.6).
     *
     * Capturée parce qu'elle porte **deux** commandes là où l'avis hors ligne
     * n'en portait qu'une : ce qu'on regarde ici, c'est qu'elles tiennent côte
     * à côte sans que le message se replie sur trois lignes, et que la seconde
     * se distingue de la première sans passer pour un ornement.
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
     * Un article court, **sans illustration** : le seul cas où l'alignement du
     * bouton de partage se voit.
     *
     * Il ne s'agit pas d'un état de plus mais d'un garde-fou. La colonne
     * intérieure de la carte épousait son contenu, et l'`align(End)` du partage
     * se rangeait donc sur la **largeur du texte** plutôt que sur celle de la
     * carte. Aucune des captures existantes ne pouvait le montrer : leurs
     * articles ont tous soit une illustration, qui impose la pleine largeur,
     * soit un texte assez long pour l'atteindre. Constaté sur l'émulateur
     * (GOAL-022-T03), sur une brève de Hacker News dont l'extrait tenait en un
     * mot.
     *
     * Le fanion des articles lus s'était fait prendre exactement de la même
     * façon en `GOAL-017-T02`. Deux fois par la même porte : la capture reste.
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
