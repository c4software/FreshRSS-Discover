package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Article cache access. */
@Dao
internal interface ArticleDao {
    /**
     * The most recent articles, read ones included, newest first.
     *
     * Sorted on publication date, not insertion order: the server may return
     * a page in any order, and the display must stay reverse-chronological
     * (SPECS.md §4.2, rule 2).
     *
     * Read articles are not excluded, and that is the core of launch
     * stability (SPECS.md §5.1). Excluding them made the set change between
     * openings — marking consumes some each session — and the shuffle, which
     * picks each position by looking at its neighbors, then produced a
     * different order: the feed appeared to reshuffle itself. Read articles
     * therefore stay displayed until the next user-requested reload, which
     * alone renews the list.
     *
     * This choice also fixed a real defect: filtering in Kotlin applied the
     * limit before filtering, so a cache whose two hundred most recent
     * articles had all been read returned an empty list — the screen believed
     * the cache empty and triggered the fallback load on every opening.
     * Observed on device: 283 articles, 69 unread, zero displayed.
     */
    @Query(
        "SELECT * FROM articles ORDER BY published_at_epoch_seconds DESC, id DESC LIMIT :limit",
    )
    fun observeArticles(limit: Int): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    /**
     * Among [ids], those already read locally.
     *
     * Bounded to the requested identifiers, not "all read articles": the
     * caller is the upsert, invoked for every 40-article page, and re-reading
     * the whole table per page would make the cost grow with history rather
     * than with the page.
     */
    @Query("SELECT id FROM articles WHERE is_read = 1 AND id IN (:ids)")
    suspend fun readArticleIdsAmong(ids: List<Long>): List<Long>

    /**
     * Unread cached articles, newest first.
     *
     * A one-shot read rather than a `Flow`: the caller is the reading reminder
     * (SPECS.md §4.9), which runs once outside any UI. A flow would force it
     * to open and close a subscription for a single reading, and to keep a
     * database observer in a worker meant to terminate.
     *
     * The filter is done by SQLite rather than in Kotlin afterwards: without
     * it, a fully read pile would fetch two hundred rows only to keep none.
     */
    @Query(
        "SELECT * FROM articles WHERE is_read = 0 " +
            "ORDER BY published_at_epoch_seconds DESC, id DESC LIMIT :limit",
    )
    suspend fun unreadArticles(limit: Int): List<ArticleEntity>

    /**
     * Saves a page while preserving the locally known read state.
     *
     * An article read on the device may not yet be read server-side: the mark
     * is queued offline and only transmitted once the network returns
     * (SPECS.md §5.2). A plain overwrite would make it reappear as unread on
     * the first refresh. The local state can therefore only progress toward
     * "read"; the server can set it but never remove it.
     */
    @Transaction
    suspend fun upsertPreservingLocalReadState(articles: List<ArticleEntity>) {
        // Empty guard: Room generates no parameter for an empty list and
        // `IN ()` is not valid SQL — same rule as `deleteExcept`.
        if (articles.isEmpty()) return
        val locallyRead = readArticleIdsAmong(articles.map(ArticleEntity::id)).toSet()
        insertAll(articles.map { if (it.id in locallyRead) it.copy(isRead = true) else it })
    }

    /**
     * Switches articles to read locally, without waiting for the server.
     *
     * The optimistic step of SPECS.md §4.5: the local state changes
     * immediately, transmission follows. The update is deliberately partial —
     * only `is_read` changes — because the rest of the article has not
     * changed, and a full rewrite would require reading it first.
     *
     * An identifier absent from the cache does nothing: the article is still
     * sent to the server, as the queue does not depend on cache presence.
     */
    @Query("UPDATE articles SET is_read = 1 WHERE id IN (:articleIds)")
    suspend fun markAsRead(articleIds: List<Long>)

    /**
     * Keeps only [keptIds], plus articles whose mark is still awaiting
     * transmission.
     *
     * This is the reload renewal (SPECS.md §4.6, GOAL-027): the server
     * response becomes the cache content. The criterion is absence from the
     * returned page, not the local read state — which knows nothing of what
     * was read elsewhere. An article read from the web UI stops being
     * returned, and that is the only signal the application ever receives:
     * measured on device, 31 articles "unread" locally while the server had
     * none left.
     *
     * The `pending_marks` subquery is the same guarantee as in the purge, and
     * weighs more here: those rows carry a truth the server does not yet know,
     * so it does not return them as read. Deleting them would lose the local
     * memory of "already read", and the article would come back as new.
     */
    @Query(
        "DELETE FROM articles WHERE id NOT IN (:keptIds) " +
            "AND id NOT IN (SELECT article_id FROM pending_marks)",
    )
    suspend fun deleteExcept(keptIds: List<Long>)

    /**
     * The same renewal when the returned page is empty.
     *
     * A separate query because `NOT IN ()` is not valid SQL: Room inserts no
     * parameter for an empty list, and the query would fail at runtime —
     * precisely in the most common case, the reader who has read everything.
     */
    @Query("DELETE FROM articles WHERE id NOT IN (SELECT article_id FROM pending_marks)")
    suspend fun deleteAllExceptPendingMarks()

    /**
     * Deletes read and synchronized articles cached before
     * [thresholdEpochMillis].
     *
     * The `is_read = 1` condition is the first guarantee of SPECS.md §5.4: an
     * unread article is never purged, whatever its age.
     *
     * The `pending_marks` subquery is the second, and fixes a real defect.
     * Without it, a device offline longer than the threshold lost read
     * articles whose mark had not yet been transmitted. The queue survived —
     * the mark would eventually arrive — but the local memory of "already
     * read" disappeared with the row: `upsertPreservingLocalReadState` reads
     * it from this table and nowhere else. On the next refresh, the server
     * described the article as unread again, nothing contradicted it, and it
     * reappeared in the feed as never read — exactly the regression the rest
     * of the cache works to prevent.
     */
    @Query(
        "DELETE FROM articles WHERE is_read = 1 " +
            "AND cached_at_epoch_millis < :thresholdEpochMillis " +
            "AND id NOT IN (SELECT article_id FROM pending_marks)",
    )
    suspend fun deleteReadCachedBefore(thresholdEpochMillis: Long): Int

    /** Observable count of kept articles: shown on the settings screen (SPECS.md §6). */
    @Query("SELECT COUNT(*) FROM articles")
    fun observeArticleCount(): Flow<Int>

    /**
     * Number of articles a purge would remove right now.
     *
     * The condition is [deleteReadCachedBefore]'s minus the age check: what
     * the manual purge button would delete. It is written twice rather than
     * shared because a `@Query` is a string literal — but the two must change
     * together, or the screen would announce a number the purge would not
     * honor.
     *
     * Room tracks both referenced tables: removing a mark from the queue
     * makes this counter rise on its own.
     */
    @Query(
        "SELECT COUNT(*) FROM articles WHERE is_read = 1 " +
            "AND id NOT IN (SELECT article_id FROM pending_marks)",
    )
    fun observePurgeableCount(): Flow<Int>

    @Query("DELETE FROM articles")
    suspend fun deleteAll()
}
