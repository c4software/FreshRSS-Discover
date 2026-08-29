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
        // A real summary reaches 34,777 characters (SPECS.md §8, question 7).
        val model = article(summary = "mot ".repeat(10_000)).toUiModel(NOW_MILLIS)

        assertTrue(model.excerpt.length <= EXCERPT_MAX_LENGTH + 1)
        assertTrue(model.excerpt.endsWith("…"))
    }

    @Test
    fun theCutFallsOnAWordBoundary() {
        val model = article(summary = "mot ".repeat(10_000)).toUiModel(NOW_MILLIS)

        // No truncated word: the cut reads as an excerpt, not as a display
        // defect.
        assertTrue(model.excerpt.removeSuffix("…").endsWith("mot"))
    }

    @Test
    fun aSummaryWithoutAnySpaceIsCutAnyway() {
        // A token or a very long URL offers no word boundary: better a hard
        // cut than displaying everything.
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
        // Without the URL, the card could only reserve the slot: the URL is
        // what makes display possible (SPECS.md §4.3).
        val model = article(imageUrl = "https://exemple.org/i.png").toUiModel(NOW_MILLIS)

        assertEquals("https://exemple.org/i.png", model.imageUrl)
        assertNull(article(imageUrl = null).toUiModel(NOW_MILLIS).imageUrl)
    }

    @Test
    fun anArticleWithoutLinkIsNotOpenable() {
        assertFalse(article(url = null).toUiModel(NOW_MILLIS).isOpenable)
        assertTrue(article(url = "https://exemple.org/a").toUiModel(NOW_MILLIS).isOpenable)
    }

    @Test
    fun anArticleAlreadyReadArrivesRead() {
        // Observed on device: the projection lost this state, so an article
        // read the day before came back from the cache as new. Its badge
        // (SPECS.md §4.5) only appeared after one second of visibility, once
        // the session's marking restored it: a visible delay at load, and a
        // wrong state in between.
        val projected = article(id = 1L, isRead = true).toUiModel(nowEpochMillis = 0L)

        assertTrue(projected.isRead)
    }

    @Test
    fun anUnreadArticleArrivesUnread() {
        assertFalse(article(id = 1L, isRead = false).toUiModel(nowEpochMillis = 0L).isRead)
    }

    // ----- The full-screen excerpt (SPECS.md §8, question 8) ------------------

    @Test
    fun theFullScreenExcerptIsLongerThanTheCardExcerpt() {
        // The whole point of SPECS.md §8 question 8: full screen shows more
        // than the three lines of a card.
        assertTrue(IMMERSIVE_EXCERPT_MAX_LENGTH > EXCERPT_MAX_LENGTH)
    }

    @Test
    fun aShortSummaryGivesTheSameExcerptToBothModes() {
        val model = article(summary = "Un extrait court.").toUiModel(NOW_MILLIS)

        assertEquals(model.excerpt, model.immersiveExcerpt)
    }

    @Test
    fun aLongSummaryIsCutOnAWordBoundaryForTheFullScreenToo() {
        val summary = "mot ".repeat(1_000)

        val excerpt = article(summary = summary).toUiModel(NOW_MILLIS).immersiveExcerpt

        assertTrue(excerpt.length <= IMMERSIVE_EXCERPT_MAX_LENGTH + 1)
        assertTrue(excerpt.length > EXCERPT_MAX_LENGTH)
        assertTrue(excerpt.endsWith("mot…"), excerpt.takeLast(10))
    }

    @Test
    fun aFullScreenSummaryWithoutAnySpaceIsCutOutright() {
        // Token or URL: with no word boundary, the cut is hard.
        val summary = "a".repeat(IMMERSIVE_EXCERPT_MAX_LENGTH * 2)

        val excerpt = article(summary = summary).toUiModel(NOW_MILLIS).immersiveExcerpt

        assertEquals(IMMERSIVE_EXCERPT_MAX_LENGTH + 1, excerpt.length)
        assertTrue(excerpt.endsWith("…"))
    }

    @Test
    fun aFullScreenSummaryExactlyAtTheLimitKeepsItsLastWord() {
        // The bound is inclusive: nothing is removed, so nothing is flagged.
        val summary = "a".repeat(IMMERSIVE_EXCERPT_MAX_LENGTH)

        val excerpt = article(summary = summary).toUiModel(NOW_MILLIS).immersiveExcerpt

        assertEquals(summary, excerpt)
        assertFalse(excerpt.endsWith("…"))
    }
}
