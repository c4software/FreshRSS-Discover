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
 * The reminder as Android receives it.
 *
 * `qualifiers = "fr-rFR"`: the wordings are the core of the feature
 * (SPECS.md §4.9), and testing them in the machine's default locale would say
 * nothing about what the user reads.
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

    /** The expanded body: what `BigTextStyle` shows, and what quotes the titles. */
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

        // Without a channel, Android 8+ silently drops the notification: the
        // channel's existence is the first thing to check.
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
        // The point of the feature: four variants of the same sentence would
        // be dismissed as one (SPECS.md §4.9).
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

        // `EXTRA_TEXT` is what the collapsed shade shows; without it, the
        // notification would only have a title until expanded.
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

        // Creating a channel while the app's notifications are disabled would
        // add an inert entry in the Android settings, right under the switch
        // the user just turned off.
        assertNull(channelOfTheReminder())
    }
}
