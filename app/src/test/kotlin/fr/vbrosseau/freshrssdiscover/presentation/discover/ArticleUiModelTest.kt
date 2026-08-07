package fr.vbrosseau.freshrssdiscover.presentation.discover

import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.feed.feedRef
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NOW_SECONDS = 1_700_000_000L
private const val NOW_MILLIS = NOW_SECONDS * 1_000L

class ArticleUiModelTest {

    @Test
    fun theSourceAndTheAgeTravelWithTheArticle() {
        val model = article(
            id = 42L,
            title = "Un titre",
            publishedAtEpochSeconds = NOW_SECONDS - 7_200L,
            feed = feedRef(title = "Le Monde"),
        ).toUiModel(NOW_MILLIS)

        assertEquals(42L, model.id)
        assertEquals("Un titre", model.title)
        assertEquals("Le Monde", model.feedTitle)
        assertEquals(RelativeTime.Hours(2), model.publishedAt)
    }

    @Test
    fun aShortSummaryIsLeftUntouched() {
        val model = article(summary = "Un extrait court.").toUiModel(NOW_MILLIS)

        assertEquals("Un extrait court.", model.excerpt)
    }

    @Test
    fun aVeryLongSummaryIsShortened() {
        // Un résumé réel atteint 34 777 caractères (SPECS.md §8, question 7).
        val model = article(summary = "mot ".repeat(10_000)).toUiModel(NOW_MILLIS)

        assertTrue(model.excerpt.length <= EXCERPT_MAX_LENGTH + 1)
        assertTrue(model.excerpt.endsWith("…"))
    }

    @Test
    fun theCutFallsOnAWordBoundary() {
        val model = article(summary = "mot ".repeat(10_000)).toUiModel(NOW_MILLIS)

        // Aucun mot tronqué : la coupure se lit comme un extrait, pas comme un
        // défaut d'affichage.
        assertTrue(model.excerpt.removeSuffix("…").endsWith("mot"))
    }

    @Test
    fun aSummaryWithoutAnySpaceIsCutAnyway() {
        // Un jeton ou une URL très longue n'offre aucune frontière de mot :
        // mieux vaut couper net que tout afficher.
        val model = article(summary = "a".repeat(1_000)).toUiModel(NOW_MILLIS)

        assertEquals(EXCERPT_MAX_LENGTH + 1, model.excerpt.length)
    }

    @Test
    fun anArticleWithoutIllustrationSaysSo() {
        assertFalse(article(imageUrl = null).toUiModel(NOW_MILLIS).hasIllustration)
        assertTrue(article(imageUrl = "https://exemple.org/i.png").toUiModel(NOW_MILLIS).hasIllustration)
    }

    @Test
    fun theIllustrationUrlTravelsToTheCard() {
        // Sans l'URL, la carte ne pouvait que réserver la place : c'est elle
        // qui rend l'affichage possible (SPECS.md §4.3).
        val model = article(imageUrl = "https://exemple.org/i.png").toUiModel(NOW_MILLIS)

        assertEquals("https://exemple.org/i.png", model.imageUrl)
        assertNull(article(imageUrl = null).toUiModel(NOW_MILLIS).imageUrl)
    }

    @Test
    fun anArticleWithoutLinkIsNotOpenable() {
        assertFalse(article(url = null).toUiModel(NOW_MILLIS).isOpenable)
        assertTrue(article(url = "https://exemple.org/a").toUiModel(NOW_MILLIS).isOpenable)
    }
}
