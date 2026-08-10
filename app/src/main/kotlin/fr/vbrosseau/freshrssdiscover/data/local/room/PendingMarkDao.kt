package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Access to the queue of read marks awaiting transmission. */
@Dao
internal interface PendingMarkDao {
    /**
     * Enqueues articles, never duplicating one already present.
     *
     * `IGNORE` rather than `REPLACE`: the existing row keeps its original
     * enqueue timestamp. With `REPLACE`, an article seen again on screen would
     * have its timestamp pushed back and fall behind the others — a
     * frequently revisited article might never be transmitted.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(marks: List<PendingMarkEntity>)

    /**
     * The [limit] oldest pending marks.
     *
     * Ordered by enqueue time: replay starts with what has waited longest.
     * `article_id` breaks ties so two successive calls return the same thing —
     * otherwise a partial transmission could keep landing on the same batch
     * indefinitely.
     */
    @Query(
        "SELECT article_id FROM pending_marks " +
            "ORDER BY queued_at_epoch_millis ASC, article_id ASC LIMIT :limit",
    )
    suspend fun pending(limit: Int): List<Long>

    /** Removes marks whose transmission is confirmed. */
    @Query("DELETE FROM pending_marks WHERE article_id IN (:articleIds)")
    suspend fun deleteByIds(articleIds: List<Long>)

    @Query("DELETE FROM pending_marks")
    suspend fun deleteAll()
}
