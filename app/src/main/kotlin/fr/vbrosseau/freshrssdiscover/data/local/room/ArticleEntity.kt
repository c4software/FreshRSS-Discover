package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An article as stored on the device.
 *
 * The primary key is the article's decimal identifier, the one the domain
 * handles (`ArticleId`). The API also exposes a hexadecimal form; letting it
 * into the database would allow two rows for the same article, and
 * mark-as-read would then fail silently.
 *
 * The feed title is duplicated on each row rather than moved to a feed table:
 * the cache has a single reader, the display, and a join for one label would
 * cost more than it saves (AGENTS.md §2 — no abstraction before its second
 * use).
 */
@Entity(tableName = "articles")
internal data class ArticleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "url")
    val url: String?,
    @ColumnInfo(name = "published_at_epoch_seconds")
    val publishedAtEpochSeconds: Long,
    @ColumnInfo(name = "summary")
    val summary: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String?,
    @ColumnInfo(name = "author")
    val author: String?,
    @ColumnInfo(name = "feed_id")
    val feedId: String,
    @ColumnInfo(name = "feed_title")
    val feedTitle: String,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean,
    /**
     * When the article entered — or was refreshed in — the cache.
     *
     * Distinct from the publication date: the purge (SPECS.md §5.4) bounds age
     * in the cache, not the article's age. Purging on publication would
     * instantly remove an old article the user just read while it is still
     * visible on screen.
     */
    @ColumnInfo(name = "cached_at_epoch_millis")
    val cachedAtEpochMillis: Long,
)
