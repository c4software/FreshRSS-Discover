package fr.vbrosseau.freshrssdiscover.domain.reminder

import fr.vbrosseau.freshrssdiscover.domain.feed.Article

/** Number of titles quoted in a reminder. */
const val REMINDER_TITLE_COUNT: Int = 2

/**
 * Possible phrasings of a reminder.
 *
 * An enum rather than strings: the domain chooses which one, the Android layer
 * knows how to say it. Every displayed string is a resource (AGENTS.md §9),
 * and the domain knows none, nor the user's language.
 *
 * Several phrasings because an identical daily reminder stops being read after
 * three days: the eye learns the shape of the message and skips it. Variation
 * is what keeps it readable.
 */
enum class ReminderTone {
    /** "Articles are waiting for you." */
    Waiting,

    /** "A moment to read?" */
    Invitation,

    /** "Here is what arrived since yesterday." */
    Fresh,

    /** "Your pile is waiting." */
    Pile,
}

/**
 * What a reminder should say, or the absence of a reminder.
 *
 * `null` returned by [reminderPlanFor] means do not notify at all. A reminder
 * announcing there is nothing to read is an interruption with no payoff, and
 * exactly what makes users disable an app's notifications.
 *
 * @property titles the quoted titles, in feed order. They come from the
 *   articles themselves: content, not UI labels, so nothing to translate.
 */
data class ReminderPlan(
    val tone: ReminderTone,
    val unreadCount: Int,
    val titles: List<String>,
)

/**
 * Decides the day's reminder from what the cache contains.
 *
 * The tone rotates with the day, deterministically: two runs on the same day
 * (a retry after failure, a device restart) yield the same message, whereas a
 * random draw would yield two different ones for a single reminder. Two
 * consecutive days never share a tone.
 *
 * @param dayIndex a strictly increasing day number, typically days since the
 *   epoch. Only its remainder matters.
 */
fun reminderPlanFor(
    unread: List<Article>,
    dayIndex: Long,
): ReminderPlan? {
    if (unread.isEmpty()) return null

    val tones = ReminderTone.entries
    // `Math.floorMod`, not `%`: a negative `dayIndex` (a pre-1970 date, which
    // a misconfigured device clock can produce) would yield a negative index
    // and the list access would fail.
    val tone = tones[Math.floorMod(dayIndex, tones.size.toLong()).toInt()]

    return ReminderPlan(
        tone = tone,
        unreadCount = unread.size,
        titles = unread.take(REMINDER_TITLE_COUNT).map(Article::title),
    )
}
