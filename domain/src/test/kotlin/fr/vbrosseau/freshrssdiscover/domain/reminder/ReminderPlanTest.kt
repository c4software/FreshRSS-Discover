package fr.vbrosseau.freshrssdiscover.domain.reminder

import fr.vbrosseau.freshrssdiscover.domain.feed.article
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderPlanTest {
    @Test
    fun anEmptyCacheProducesNoReminderAtAll() {
        // Un rappel annonçant qu'il n'y a rien à lire est une interruption sans
        // contrepartie — et c'est exactement ce qui fait couper les
        // notifications d'une application.
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
        // Le nombre porte sur la pile entière : citer deux titres sur douze et
        // annoncer « 2 » ferait passer le rappel pour un inventaire complet.
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
        // Une reprise après échec ou un redémarrage de l'appareil rejoue le
        // même jour : un tirage au hasard donnerait deux messages pour un seul
        // rappel.
        val unread = listOf(article(id = 1L))

        assertEquals(
            reminderPlanFor(unread, dayIndex = 20_000)?.tone,
            reminderPlanFor(unread, dayIndex = 20_000)?.tone,
        )
    }

    @Test
    fun twoConsecutiveDaysNeverShareTheirMessage() {
        // C'est la raison d'être de la variation : l'œil apprend la forme d'un
        // message quotidien identique et le balaie sans le lire.
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
        // Une formulation qui ne sortirait jamais serait du code mort déguisé
        // en variété (AGENTS.md §2).
        val unread = listOf(article(id = 1L))
        val seen = (0L until ReminderTone.entries.size.toLong()).mapNotNull { reminderPlanFor(unread, it)?.tone }

        assertEquals(ReminderTone.entries.toSet(), seen.toSet())
    }

    @Test
    fun aClockSetBeforeTheEpochStillProducesAMessage() {
        // Un appareil dont l'horloge est manifestement fausse rend un numéro de
        // jour négatif. Un reste négatif ferait échouer l'accès à la liste, et
        // le rappel planterait au lieu d'être médiocre.
        val plan = reminderPlanFor(listOf(article(id = 1L)), dayIndex = -3L)

        assertTrue(plan?.tone in ReminderTone.entries)
    }
}
