package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.presentation.discover.EXCERPT_MAX_LENGTH
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NOW_SECONDS = 1_700_000_000L
private const val NOW_MILLIS = NOW_SECONDS * 1_000L

class SwipeExcerptTest {

    @Test
    fun aShortSummaryIsShownWhole() {
        val model = article(summary = "Un extrait court.").toSwipeUiModel(NOW_MILLIS)

        assertEquals("Un extrait court.", model.excerpt)
    }

    @Test
    fun theFullScreenExcerptIsLongerThanTheCardExcerpt() {
        // C'est tout l'objet de SPECS.md §8 question 8 : le plein écran montre
        // davantage que les trois lignes d'une carte.
        assertTrue(SWIPE_EXCERPT_MAX_LENGTH > EXCERPT_MAX_LENGTH)
    }

    @Test
    fun aLongSummaryIsCutOnAWordBoundary() {
        val summary = "mot ".repeat(1_000)

        val excerpt = article(summary = summary).toSwipeUiModel(NOW_MILLIS).excerpt

        assertTrue(excerpt.length <= SWIPE_EXCERPT_MAX_LENGTH + 1)
        assertTrue(excerpt.endsWith("mot…"), excerpt.takeLast(10))
    }

    @Test
    fun aSummaryWithoutAnySpaceIsCutOutright() {
        // Jeton ou URL : faute de frontière de mot, la coupure est nette.
        val summary = "a".repeat(SWIPE_EXCERPT_MAX_LENGTH * 2)

        val excerpt = article(summary = summary).toSwipeUiModel(NOW_MILLIS).excerpt

        assertEquals(SWIPE_EXCERPT_MAX_LENGTH + 1, excerpt.length)
        assertTrue(excerpt.endsWith("…"))
    }

    @Test
    fun aSummaryExactlyAtTheLimitKeepsItsLastWord() {
        // La borne est inclusive : rien n'est retiré, donc rien n'est signalé.
        val summary = "a".repeat(SWIPE_EXCERPT_MAX_LENGTH)

        val excerpt = article(summary = summary).toSwipeUiModel(NOW_MILLIS).excerpt

        assertEquals(summary, excerpt)
        assertFalse(excerpt.endsWith("…"))
    }

    @Test
    fun everythingElseComesFromTheListProjection() {
        // Seule la longueur de l'extrait change : le reste doit rester d'une
        // seule pièce entre les deux modes (SPECS.md §4.8).
        val model = article(
            id = 7L,
            title = "Un titre",
            url = "https://exemple.org/a",
            publishedAtEpochSeconds = NOW_SECONDS - 7_200L,
            imageUrl = "https://exemple.org/i.jpg",
        ).toSwipeUiModel(NOW_MILLIS)

        assertEquals(7L, model.id)
        assertEquals("Un titre", model.title)
        assertEquals("Un flux", model.feedTitle)
        assertEquals(RelativeTime.Hours(2), model.publishedAt)
        assertTrue(model.hasIllustration)
        assertTrue(model.isOpenable)
    }
}
