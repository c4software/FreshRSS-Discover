package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.presentation.ScreenshotTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Références visuelles des illustrations d'articles.
 *
 * Elles vivent à part des captures d'écrans : ce qu'on y regarde n'est pas la
 * mise en page mais le **rendu de l'image** — sa présence, sa disparition en cas
 * d'échec, et le contraste du créneau réservé, qui était le défaut à corriger.
 *
 * Le chargeur d'images est remplacé par un moteur déterministe : sans lui la
 * référence dépendrait du réseau, donc ne serait pas une référence.
 */
class DiscoverMediaScreenshotTest : ScreenshotTest() {

    @Before
    fun installImageLoader() = installFakeImageLoader()

    @After
    fun restoreImageLoader() = resetImageLoader()

    /**
     * Les trois issues côte à côte, volontairement sur une seule image.
     *
     * C'est leur juxtaposition qui montre l'essentiel : ni l'article sans image
     * ni celui dont l'image a échoué ne laissent de trou dans la colonne
     * (SPECS.md §4.3).
     */
    @Test
    fun illustrationsInTheirThreeOutcomes() {
        capture("discover-illustrations") {
            DiscoverScreen(
                uiState = DiscoverUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "L'illustration a été chargée",
                            imageUrl = LOADABLE_IMAGE_URL,
                        ),
                        sampleArticle(id = 2L, title = "Cet article n'a pas d'illustration"),
                        sampleArticle(
                            id = 3L,
                            title = "L'illustration n'a pas pu être chargée",
                            imageUrl = UNREACHABLE_IMAGE_URL,
                        ),
                    ),
                    phase = DiscoverPhase.EndOfFeed,
                ),
                onLoadMore = {},
                onRetry = {},
                onArticleClick = {},
                onArticleShare = {},
            )
        }
    }

    /**
     * Le créneau réservé, image non encore arrivée.
     *
     * C'est la capture qui expose le défaut corrigé : la teinte du réservé doit
     * se distinguer du conteneur de la carte en thème **clair** autant qu'en
     * sombre. L'ancienne, tirée de `surfaceVariant`, ne s'en distinguait qu'en
     * sombre.
     */
    @Test
    fun theReservedSlotIsVisibleBeforeTheImageArrives() {
        capture("discover-illustration-attente") {
            DiscoverScreen(
                uiState = DiscoverUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "L'illustration est en cours de chargement",
                            imageUrl = PENDING_IMAGE_URL,
                        ),
                    ),
                    phase = DiscoverPhase.EndOfFeed,
                ),
                onLoadMore = {},
                onRetry = {},
                onArticleClick = {},
                onArticleShare = {},
            )
        }
    }

    /**
     * Une illustration **plus petite que le créneau**, à côté d'une grande.
     *
     * C'est la comparaison qui juge le procédé (SPECS.md §4.3) : la petite doit
     * apparaître à sa taille, centrée sur un fond flouté tiré d'elle-même,
     * plutôt qu'étirée et pixelisée. La grande, elle, ne doit **pas** avoir
     * changé — c'est ce qui établit que le fond ne s'applique qu'au cas visé.
     */
    @Test
    fun aTinyIllustrationSitsOnItsOwnBlurredBackdrop() {
        capture("discover-illustration-minuscule") {
            DiscoverScreen(
                uiState = DiscoverUiState(
                    articles = listOf(
                        sampleArticle(
                            id = 1L,
                            title = "Une illustration plus petite que le créneau",
                            imageUrl = TINY_IMAGE_URL,
                        ),
                        sampleArticle(
                            id = 2L,
                            title = "Une illustration assez grande, inchangée",
                            imageUrl = LOADABLE_IMAGE_URL,
                        ),
                    ),
                    phase = DiscoverPhase.EndOfFeed,
                ),
                onLoadMore = {},
                onRetry = {},
                onArticleClick = {},
                onArticleShare = {},
            )
        }
    }
}

private fun sampleArticle(
    id: Long,
    title: String,
    imageUrl: String? = null,
): ArticleUiModel = ArticleUiModel(
    id = id,
    title = title,
    feedTitle = "Le Monde — Sciences",
    publishedAt = RelativeTime.Hours(2),
    excerpt = "Un extrait de l'article, écourté par l'application avant l'affichage.",
    imageUrl = imageUrl,
    isOpenable = true,
)
