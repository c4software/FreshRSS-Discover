package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An article whose transition to read has yet to be transmitted to the server.
 *
 * This table exists because marking is optimistic (SPECS.md §4.5): the local
 * state changes as soon as the article has been seen, the send follows, and a
 * network failure must not be visible during reading. The intent therefore
 * needs a place that survives failure — and application shutdown, hence Room
 * rather than an in-memory list: an untransmitted mark is replayed even after
 * a restart.
 *
 * The primary key is the article identifier itself: the queue describes a set
 * of articles to mark, not a sequence of events. Two passes over the same
 * article have nothing more to transmit than one, and an auto-generated key
 * would grow the queue with ineffective duplicates.
 */
@Entity(tableName = "pending_marks")
internal data class PendingMarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    /**
     * Enqueue timestamp.
     *
     * Drives the replay order: the oldest mark leaves first. Without it, the
     * order would be whatever SQLite happens to return.
     */
    @ColumnInfo(name = "queued_at_epoch_millis")
    val queuedAtEpochMillis: Long,
)
