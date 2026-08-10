package fr.vbrosseau.freshrssdiscover.domain.reminder

import fr.vbrosseau.freshrssdiscover.domain.feed.article
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderPlanTest {
    @Test
    fun anEmptyCacheProducesNoReminderAtAll() {
        // A reminder announcing there is nothing to read is an interruption
        // with no payoff, exactly what gets an app's notifications disabled.
        assertNull(reminderPlanFor(unread = emptyList(), dayIndex = 0))
    }

    @Test
    fun theReminderCitesTheFirstTitlesOfTheFeed() {
        val plan =
            reminderPlanFor(
                unread =
                    listOf(
                        article(id = 1L, title = "Premier"),
                        article(id = 2L, title = "Deuxième"),
                        article(id = 3L, title = "Troisième"),
                    ),
                dayIndex = 0,
            )

        assertEquals(listOf("Premier", "Deuxième"), plan?.titles)
    }

    @Test
    fun theReminderCountsEverythingEvenWhatItDoesNotName() {
        // The count covers the whole backlog: quoting two titles out of twelve
        // while announcing "2" would make the reminder look like a complete
        // inventory.
        val plan = reminderPlanFor(unread = List(12) { article(id = it.toLong()) }, dayIndex = 0)

        assertEquals(12, plan?.unreadCount)
        assertEquals(REMINDER_TITLE_COUNT, plan?.titles?.size)
    }

    @Test
    fun aFeedShorterThanTheQuotaCitesWhatItHas() {
        val plan = reminderPlanFor(unread = listOf(article(id = 1L, title = "Seul")), dayIndex = 0)

        assertEquals(listOf("Seul"), plan?.titles)
        assertEquals(1, plan?.unreadCount)
    }

    @Test
    fun theSameDayAlwaysGivesTheSameMessage() {
        // A retry after failure or a device reboot replays the same day: a
        // random draw would give two messages for a single reminder.
        val unread = listOf(article(id = 1L))

        assertEquals(
            reminderPlanFor(unread, dayIndex = 20_000)?.tone,
            reminderPlanFor(unread, dayIndex = 20_000)?.tone,
        )
    }

    @Test
    fun twoConsecutiveDaysNeverShareTheirMessage() {
        // The point of the variation: the eye learns the shape of an
        // identical daily message and skims past it without reading.
        val unread = listOf(article(id = 1L))

        for (day in 0L until 40L) {
            assertEquals(
                false,
                reminderPlanFor(unread, day)?.tone == reminderPlanFor(unread, day + 1)?.tone,
                "jours $day et ${day + 1}",
            )
        }
    }

    @Test
    fun everyToneIsUsedOverACompleteCycle() {
        // A phrasing that never comes up would be dead code disguised as
        // variety (AGENTS.md §2).
        val unread = listOf(article(id = 1L))
        val seen = (0L until ReminderTone.entries.size.toLong()).mapNotNull { reminderPlanFor(unread, it)?.tone }

        assertEquals(ReminderTone.entries.toSet(), seen.toSet())
    }

    @Test
    fun aClockSetBeforeTheEpochStillProducesAMessage() {
        // A device with a blatantly wrong clock yields a negative day number.
        // A negative remainder would make the list access fail, and the
        // reminder would crash instead of merely being mediocre.
        val plan = reminderPlanFor(listOf(article(id = 1L)), dayIndex = -3L)

        assertTrue(plan?.tone in ReminderTone.entries)
    }
}
