package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.presentation.ScreenshotTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Visual references for article illustrations.
 *
 * Kept apart from the screen captures: what is examined here is not the layout
 * but the image rendering — its presence, its disappearance on failure, and
 * the contrast of the reserved slot.
 *
 * The image loader is replaced by a deterministic engine: without it the
 * reference would depend on the network and would not be a reference.
 */
class DiscoverMediaScreenshotTest : ScreenshotTest() {

    @Before
    fun installImageLoader() = installFakeImageLoader()

    @After
    fun restoreImageLoader() = resetImageLoader()

    /**
     * The three outcomes side by side, deliberately in a single image.
     *
     * Their juxtaposition shows the essential point: neither the article
     * without an image nor the one whose image failed leaves a hole in the
     * column (SPECS.md §4.3).
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
     * The reserved slot, image not yet arrived.
     *
     * The reserved tint must stand out from the card container in the light
     * theme as much as in the dark one. The previous tint, drawn from
     * `surfaceVariant`, only stood out in dark.
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
     * An illustration smaller than the slot, next to a large one.
     *
     * The comparison judges the technique (SPECS.md §4.3): the small one must
     * appear at its own size, centered on a blurred backdrop drawn from
     * itself, rather than stretched and pixelated. The large one must not have
     * changed, which establishes that the backdrop only applies to the
     * targeted case.
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
