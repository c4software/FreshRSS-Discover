package fr.vbrosseau.freshrssdiscover.reminder

import android.content.Intent
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a touch on the reminder carries to the feed (SPECS.md §4.9, GOAL-044). */
@RunWith(RobolectricTestRunner::class)
class ReminderOpeningTest {

    private val quoted = listOf(ArticleId(42L), ArticleId(7L))

    @Test
    fun theIntentGivesBackTheQuotedArticlesInTheirOrder() {
        assertEquals(quoted, Intent().quoting(quoted).quotedArticleIds())
    }

    @Test
    fun anIntentThatDoesNotComeFromTheReminderQuotesNothing() {
        assertTrue(Intent(Intent.ACTION_MAIN).quotedArticleIds().isEmpty())
    }

    @Test
    fun anIntentReplayedFromTheRecentsQuotesNothing() {
        val replayed = Intent().quoting(quoted).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertTrue(replayed.quotedArticleIds().isEmpty())
    }

    @Test
    fun theQuotedArticlesAreHandedOverOnlyOnce() {
        val opening = ReminderOpening()
        opening.openedOn(quoted)

        assertEquals(quoted, opening.take())
        assertTrue(opening.take().isEmpty())
    }
}
