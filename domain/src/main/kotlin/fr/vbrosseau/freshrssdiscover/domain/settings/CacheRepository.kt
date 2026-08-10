package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Contents of the local cache, as the settings screen must show them
 * (SPECS.md §6).
 *
 * Two article counts, and no byte count. The database file size is accessible
 * but does not measure the cache: SQLite does not return its pages to the
 * system when rows disappear, it keeps them for rewriting. A purge would
 * therefore leave the megabyte count unchanged, which the user would read as a
 * purge with no effect. The app's real footprint is already given, correctly
 * and in one place, by Android's storage settings; repeating it here would
 * only add a second, less accurate source.
 *
 * [purgeableCount] answers the only question a "Purge" button raises: what is
 * lost. It visibly changes when the button is pressed.
 */
data class CacheStatus(
    /** All retained articles, read and unread alike. */
    val articleCount: Int,
    /**
     * Articles a purge would remove: read and synchronized (SPECS.md §5.4).
     *
     * A read article whose marking has not yet been transmitted is excluded;
     * see the guarantee described on [purgeReadArticles].
     */
    val purgeableCount: Int,
) {
    companion object {
        /** Empty cache: initial value, before any database read. */
        val Empty: CacheStatus = CacheStatus(articleCount = 0, purgeableCount = 0)
    }
}

/**
 * Measurement and purge of the local cache (SPECS.md §5.4, §6).
 *
 * Separate from `SettingsRepository`: that one describes preferences the user
 * chooses, this one a device state the user observes. Merging them would blur
 * which half of the settings screen observes what.
 */
interface CacheRepository {
    /**
     * Observable cache state.
     *
     * A [Flow] rather than a one-shot read: the article count changes while
     * the screen is open (a manual purge lowers it, a sync raises it), and a
     * frozen number would cast doubt on the purge just triggered.
     */
    fun observeCacheStatus(): Flow<CacheStatus>

    /**
     * Purges immediately everything read and synchronized, with no age
     * condition. Returns the number of deleted articles.
     *
     * The manual purge of SPECS.md §6: same rule as the automatic purge, only
     * the age threshold is dropped. It can therefore remove neither an unread
     * article nor one whose marking is still awaiting transmission, which is
     * exactly what makes it safe to trigger without confirmation.
     */
    suspend fun purgeReadArticles(): Int

    companion object {
        /**
         * Age threshold of the automatic purge: 7 days.
         *
         * Answer to SPECS.md §8, question 3. Past the threshold, a read
         * article has no reader left. Below it, it has two.
         *
         * The first is backward scrolling. The feed is continuous and without
         * landmarks (SPECS.md §1); scrolling back is the only way to find
         * again what was skimmed the day before. A 24 h threshold would erase
         * that past between launches, visibly. A week covers real usage
         * rhythms: coming back on Monday finds the feed where it was left on
         * Friday.
         *
         * The second is the "already read" memory, carried by the articles
         * table (`upsertPreservingLocalReadState`). The sync condition
         * guarantees a purged article is already known read by the server, so
         * its memory lives elsewhere, but it guarantees nothing about how many
         * days the server keeps. A week leaves the server time to return the
         * same response.
         *
         * Not 30 days: the cache would quadruple with already-consumed
         * content whose only reader would be a month-long backward scroll
         * nobody performs. At 40 articles per page (SPECS.md §8, question 1)
         * and a few pages per session, 7 days cap the cache at a few thousand
         * articles.
         */
        val MaxAge: Duration = 7.days
    }
}
