package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local application database.
 *
 * `exportSchema = true`: the schema is versioned in `app/schemas/`, which lets
 * Room verify migrations automatically and makes database changes visible in a
 * review diff (ARCHITECTURE.md §5.4).
 */
@Database(
    entities = [ArticleEntity::class, PendingMarkEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    abstract fun pendingMarkDao(): PendingMarkDao
}

/**
 * Adds the pending-marks queue (`pending_marks`).
 *
 * A real migration, not `fallbackToDestructiveMigration`: a version 1 database
 * may already exist on a device, and destroying it would wipe the article
 * cache — hence, offline, all readable content (SPECS.md §5.2). The table is
 * simply created; no version 1 data is touched, since nothing that existed
 * changes shape.
 *
 * The `CREATE TABLE` statement is copied verbatim from the exported schema
 * `app/schemas/…/2.json`: this is the only way to get a migrated database
 * strictly identical to one created from scratch. The slightest difference —
 * a missing `NOT NULL`, a type spelled differently — would fail Room's
 * identity validation at startup, and only for users who migrate.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_marks` (" +
                "`article_id` INTEGER NOT NULL, " +
                "`queued_at_epoch_millis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`article_id`))",
        )
    }
}
