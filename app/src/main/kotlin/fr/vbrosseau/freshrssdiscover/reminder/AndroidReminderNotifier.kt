package fr.vbrosseau.freshrssdiscover.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.freshrssdiscover.MainActivity
import fr.vbrosseau.freshrssdiscover.R
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderPlan
import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderTone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Channel id. Frozen: changing it would create a second channel in Android
 * settings and reset a reminder the user had configured, since Android never
 * carries channel preferences over to another channel.
 */
private const val CHANNEL_ID = "rappel-de-lecture"

/**
 * Notification id, deliberately constant.
 *
 * Today's reminder replaces yesterday's instead of stacking beside it: only
 * the latest is up to date. This is also what makes
 * [ReminderNotifier.dismiss] possible without keeping a registry.
 */
private const val REMINDER_NOTIFICATION_ID = 1

/** No data varies between reminders: a single request code suffices. */
private const val OPEN_APPLICATION_REQUEST_CODE = 0

/**
 * The reading reminder as Android displays it (SPECS.md §4.9).
 *
 * `@Singleton` so the channel is described once per process, not because the
 * class holds state: it has none.
 */
@Singleton
class AndroidReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val notifications = NotificationManagerCompat.from(context)

    override fun show(plan: ReminderPlan) {
        // Checked here rather than when scheduling: permission can be revoked
        // from Android settings at any time, and a full day passes between
        // scheduling and delivery.
        if (!notifications.areNotificationsEnabled()) return

        // Created on each send rather than once at startup: the call is
        // idempotent (Android ignores an already-described channel without
        // overwriting the user's settings), and the app avoids describing a
        // channel it may never use.
        createChannel()

        val body = bodyOf(plan)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(plan.tone.titleRes()))
            .setContentText(body)
            // The body quotes real titles: collapsed, it is cut to one line
            // where an article title rarely fits. `BigTextStyle` makes it
            // readable once expanded.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApplication())
            .build()

        notifications.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    override fun dismiss() {
        // No-op when nothing is shown: `cancel` on an unknown id does not
        // throw, so the caller need not know whether a reminder was posted or
        // already dismissed by the user.
        notifications.cancel(REMINDER_NOTIFICATION_ID)
    }

    /**
     * Describes the reminder channel.
     *
     * Mandatory since Android 8: a notification posted without a channel is
     * silently dropped, and the project's `minSdk` of 26 leaves no version
     * exempt.
     *
     * Default importance: the reminder appears in the shade and status bar
     * without a heads-up popup. A reading reminder is not urgent.
     */
    private fun createChannel() {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()

        notifications.createNotificationChannel(channel)
    }

    /**
     * Opens the application on tap.
     *
     * `FLAG_IMMUTABLE`: since Android 12 a `PendingIntent` must declare its
     * mutability, and omitting it throws at construction. Immutable is right
     * here: nothing needs to fill in this intent, and a mutable intent handed
     * to the system would let another app change its destination.
     *
     * `FLAG_UPDATE_CURRENT` because the id is constant: without it, today's
     * reminder would reuse the intent of the first reminder ever posted.
     */
    private fun openApplication(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            // The reminder fires outside any activity stack: without
            // `NEW_TASK` there would be no task to place the screen in.
            // `CLEAR_TOP` brings an already-running app to the foreground
            // instead of stacking a second instance.
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            OPEN_APPLICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Quoted titles, then the remaining-unread count.
     *
     * Assembled through a format resource rather than Kotlin concatenation
     * (AGENTS.md §9): the order of the two parts and their separator belong
     * to the language.
     */
    private fun bodyOf(plan: ReminderPlan): String {
        val remaining = context.resources.getQuantityString(
            R.plurals.reminder_remaining,
            plan.unreadCount,
            plan.unreadCount,
        )

        val quoted = quotedTitles(plan.titles) ?: return remaining

        return context.getString(R.string.reminder_body, quoted, remaining)
    }

    /**
     * Article titles, quoted verbatim.
     *
     * Neither translated nor truncated: they are content, and `BigTextStyle`
     * shows them in full. The domain already keeps at most two
     * (`REMINDER_TITLE_COUNT`); beyond that, only the first two are cited,
     * which the `else` accepts rather than failing.
     *
     * `null` when there is no title: the body then reduces to the count,
     * which beats an empty pair of quotes.
     */
    private fun quotedTitles(titles: List<String>): String? = when (titles.size) {
        0 -> null
        1 -> context.getString(R.string.reminder_titles_one, titles[0])
        else -> context.getString(R.string.reminder_titles_two, titles[0], titles[1])
    }
}

/**
 * Maps the day's tone to its title resource.
 *
 * The `when` is exhaustive: adding a tone without a title fails to compile,
 * preventing a silent fallback to the identical daily message that
 * SPECS.md §4.9 exists to avoid.
 */
@StringRes
private fun ReminderTone.titleRes(): Int = when (this) {
    ReminderTone.Waiting -> R.string.reminder_title_waiting
    ReminderTone.Invitation -> R.string.reminder_title_invitation
    ReminderTone.Fresh -> R.string.reminder_title_fresh
    ReminderTone.Pile -> R.string.reminder_title_pile
}
