package fr.vbrosseau.freshrssdiscover.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.MainActivity
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderPlan
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderTone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le rappel tel qu'Android le reçoit.
 *
 * `qualifiers = "fr-rFR"` : les formulations sont le cœur de la fonctionnalité
 * (SPECS.md §4.9), et les éprouver dans la langue par défaut de la machine ne
 * dirait rien de ce que l'utilisateur lit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class AndroidReminderNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val systemNotifications = context.getSystemService(NotificationManager::class.java)

    private val notifier = AndroidReminderNotifier(context)

    private fun plan(
        tone: ReminderTone = ReminderTone.Waiting,
        unreadCount: Int = 12,
        titles: List<String> = listOf("Un titre", "Un autre"),
    ) = ReminderPlan(tone = tone, unreadCount = unreadCount, titles = titles)

    private fun posted(): List<Notification> = shadowOf(systemNotifications).allNotifications

    private fun onlyPosted(): Notification = posted().single()

    private fun titleOf(notification: Notification): String? =
        notification.extras.getString(Notification.EXTRA_TITLE)

    /** Le corps déplié : c'est celui que `BigTextStyle` montre, et celui qui cite les titres. */
    private fun bodyOf(notification: Notification): String? =
        notification.extras.getString(Notification.EXTRA_BIG_TEXT)

    private fun channelOfTheReminder(): NotificationChannel? =
        systemNotifications.getNotificationChannel("rappel-de-lecture")

    private fun refuseNotifications() {
        shadowOf(systemNotifications).setNotificationsEnabled(false)
    }

    @Test
    fun theChannelIsDescribedBeforeTheFirstReminderIsPosted() {
        notifier.show(plan())

        // Sans canal, Android 8 et au-delà écartent la notification sans rien
        // dire : l'existence du canal est donc la première chose à constater.
        assertNotNull(channelOfTheReminder())
    }

    @Test
    fun theChannelCarriesItsFrenchNameAndDescription() {
        notifier.show(plan())

        val channel = assertNotNull(channelOfTheReminder())

        assertEquals("Rappel de lecture", channel.name)
        assertTrue(channel.description.startsWith("Une notification par jour"))
    }

    @Test
    fun eachToneGivesItsOwnTitle() {
        val titles = ReminderTone.entries.map { tone ->
            notifier.show(plan(tone = tone))
            titleOf(onlyPosted())
        }

        assertEquals(
            listOf(
                "Des articles vous attendent",
                "Un moment pour lire ?",
                "Voici ce qui est arrivé depuis hier",
                "Votre pile n'attend que vous",
            ),
            titles,
        )
    }

    @Test
    fun noTwoTonesShareTheirWording() {
        // Le point de la fonctionnalité : quatre variantes d'une même phrase
        // seraient balayées comme une seule (SPECS.md §4.9).
        val titles = ReminderTone.entries.map { tone ->
            notifier.show(plan(tone = tone))
            titleOf(onlyPosted())
        }

        assertEquals(ReminderTone.entries.size, titles.toSet().size)
    }

    @Test
    fun theArticleTitlesAppearInTheBody() {
        notifier.show(plan(titles = listOf("Le prix du cuivre", "Une histoire de rails")))

        val body = bodyOf(onlyPosted())

        assertNotNull(body)
        assertTrue(body.contains("Le prix du cuivre"), body)
        assertTrue(body.contains("Une histoire de rails"), body)
    }

    @Test
    fun aSingleArticleIsAnnouncedInTheSingular() {
        notifier.show(plan(unreadCount = 1, titles = listOf("Seul au monde")))

        val body = bodyOf(onlyPosted())

        assertNotNull(body)
        assertTrue(body.contains("1 article non lu"), body)
    }

    @Test
    fun severalArticlesAreAnnouncedInThePlural() {
        notifier.show(plan(unreadCount = 12))

        val body = bodyOf(onlyPosted())

        assertNotNull(body)
        assertTrue(body.contains("12 articles non lus"), body)
    }

    @Test
    fun aPlanWithoutTitlesStillAnnouncesWhatRemains() {
        notifier.show(plan(unreadCount = 3, titles = emptyList()))

        assertEquals("3 articles non lus", bodyOf(onlyPosted()))
    }

    @Test
    fun theBodyIsAlsoReadableWhenTheNotificationIsFolded() {
        notifier.show(plan())

        // `EXTRA_TEXT` est ce que montre le volet replié ; sans lui, la
        // notification n'aurait qu'un titre tant qu'on ne la déplie pas.
        assertEquals(
            bodyOf(onlyPosted()),
            onlyPosted().extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun touchingTheReminderOpensTheApplication() {
        notifier.show(plan())

        val intent = shadowOf(onlyPosted().contentIntent).savedIntent

        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun theReminderDisappearsOnceItHasBeenTouched() {
        notifier.show(plan())

        val flags = onlyPosted().flags

        assertTrue(flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun aSecondReminderReplacesTheFirstInsteadOfStackingBesideIt() {
        notifier.show(plan(tone = ReminderTone.Waiting, unreadCount = 3))
        notifier.show(plan(tone = ReminderTone.Pile, unreadCount = 9))

        assertEquals(1, posted().size)
        assertEquals("Votre pile n'attend que vous", titleOf(onlyPosted()))
    }

    @Test
    fun dismissRemovesTheReminderOnDisplay() {
        notifier.show(plan())

        notifier.dismiss()

        assertTrue(posted().isEmpty())
    }

    @Test
    fun dismissDoesNothingWhenNoReminderIsOnDisplay() {
        notifier.dismiss()

        assertTrue(posted().isEmpty())
    }

    @Test
    fun nothingIsPostedWhenTheSystemRefusesNotifications() {
        refuseNotifications()

        notifier.show(plan())

        assertTrue(posted().isEmpty())
    }

    @Test
    fun noChannelIsDescribedWhenTheSystemRefusesNotifications() {
        refuseNotifications()

        notifier.show(plan())

        // Décrire un canal pour une application dont les notifications sont
        // coupées ferait apparaître une entrée inerte dans les réglages
        // Android, juste sous l'interrupteur que l'utilisateur vient d'éteindre.
        assertNull(channelOfTheReminder())
    }
}
