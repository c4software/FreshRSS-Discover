package fr.vbrosseau.freshrssdiscover.reminder

import android.content.Intent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import javax.inject.Inject

private const val EXTRA_QUOTED_ARTICLE_IDS = "fr.vbrosseau.freshrssdiscover.reminder.QUOTED_ARTICLE_IDS"

/** Attaches the articles a reminder quotes to the intent its touch fires. */
internal fun Intent.quoting(ids: List<ArticleId>): Intent =
    putExtra(EXTRA_QUOTED_ARTICLE_IDS, ids.map(ArticleId::value).toLongArray())

/**
 * The articles quoted by the reminder this intent comes from, in its order;
 * empty for any other way of opening the application.
 *
 * Empty as well when Android replays the intent from the recents screen: the
 * extras survive there, and the feed would open on yesterday's reminder each
 * time the task is brought back after a back press.
 */
internal fun Intent.quotedArticleIds(): List<ArticleId> {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return emptyList()

    return getLongArrayExtra(EXTRA_QUOTED_ARTICLE_IDS)?.map(::ArticleId).orEmpty()
}

/**
 * What the reminder that opened the application had quoted (SPECS.md §4.9).
 *
 * Carries the ids from the activity, which receives the intent, to the feed,
 * which decides its first order. Activity-retained: touching the reminder
 * recreates the activity, hence one instance per opening, and a rotation
 * keeps it.
 */
@ActivityRetainedScoped
class ReminderOpening @Inject constructor() {
    private var quotedIds: List<ArticleId> = emptyList()

    fun openedOn(ids: List<ArticleId>) {
        quotedIds = ids
    }

    /**
     * Hands the ids over once. A feed rebuilt later under the same activity —
     * after a sign-out and a sign-in — must not open on a reminder already
     * answered.
     */
    fun take(): List<ArticleId> = quotedIds.also { quotedIds = emptyList() }
}
