package fr.vbrosseau.freshrssdiscover.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val LARGE_LIMIT = 100
private const val START_MILLIS = 1_000_000L

@RunWith(RobolectricTestRunner::class)
class PendingMarkQueueTest {
    // In-memory database: the queue is exercised against the real SQLite
    // engine, the only thing that can reveal a primary-key constraint or
    // invalid query a fake DAO would let through.
    private val database = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()

    private val clock = FakeClock(nowMillis = START_MILLIS)
    private val queue = PendingMarkQueue(database.pendingMarkDao(), clock)

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun pendingIds(limit: Int = LARGE_LIMIT): List<Long> =
        queue.pending(limit).map { it.value }

    // ----- Enqueueing --------------------------------------------------------

    @Test
    fun anEnqueuedArticleIsPending() = runTest {
        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L)))

        assertEquals(listOf(1L, 2L), pendingIds())
    }

    @Test
    fun anEmptyBatchLeavesTheQueueUntouched() = runTest {
        queue.enqueue(listOf(ArticleId(1L)))

        queue.enqueue(emptyList())

        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun enqueuingTheSameArticleTwiceLeavesASingleEntry() = runTest {
        // Scrolling revisits the same articles. The queue describes what
        // remains to tell the server, not what happened on screen: a
        // duplicate adds nothing and grows the queue.
        queue.enqueue(listOf(ArticleId(7L)))
        clock.advanceBy(START_MILLIS)

        queue.enqueue(listOf(ArticleId(7L), ArticleId(8L)))

        assertEquals(listOf(7L, 8L), pendingIds())
    }

    @Test
    fun anArticleSeenAgainKeepsItsOriginalPlaceInTheQueue() = runTest {
        // Refreshing the timestamp on each new view would push a frequently
        // revisited article back endlessly: it might never be transmitted.
        queue.enqueue(listOf(ArticleId(7L)))
        clock.advanceBy(START_MILLIS)
        queue.enqueue(listOf(ArticleId(8L)))

        queue.enqueue(listOf(ArticleId(7L)))

        assertEquals(listOf(7L), pendingIds(limit = 1))
    }

    // ----- Batched reads -----------------------------------------------------

    @Test
    fun pendingReturnsTheOldestMarksFirstAndNoMoreThanTheLimit() = runTest {
        // Sending is batched: a queue accumulated over days offline must not
        // leave in a single oversized request.
        queue.enqueue(listOf(ArticleId(3L)))
        clock.advanceBy(START_MILLIS)
        queue.enqueue(listOf(ArticleId(1L)))
        clock.advanceBy(START_MILLIS)
        queue.enqueue(listOf(ArticleId(2L)))

        assertEquals(listOf(3L, 1L), pendingIds(limit = 2))
    }

    @Test
    fun readingTheQueueDoesNotEmptyIt() = runTest {
        // The central point: removing on read would lose the mark as soon as
        // a request fails, which this queue exists precisely to prevent
        // (SPECS.md §4.5).
        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L)))

        queue.pending(LARGE_LIMIT)

        assertEquals(listOf(1L, 2L), pendingIds())
    }

    // ----- Acknowledgement ---------------------------------------------------

    @Test
    fun acknowledgingRemovesOnlyWhatWasTransmitted() = runTest {
        // A batch may only be partially transmitted (one request succeeds,
        // the next fails). The rest must survive to be replayed.
        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L), ArticleId(3L)))

        queue.acknowledge(listOf(ArticleId(1L), ArticleId(3L)))

        assertEquals(listOf(2L), pendingIds())
    }

    @Test
    fun acknowledgingAnArticleAbsentFromTheQueueIsHarmless() = runTest {
        // Real case: two replay attempts overlap after a restart. The second
        // acknowledgement must not fail.
        queue.enqueue(listOf(ArticleId(1L)))

        queue.acknowledge(listOf(ArticleId(1L), ArticleId(99L)))

        assertTrue(pendingIds().isEmpty())
    }

    // ----- Clearing ----------------------------------------------------------

    @Test
    fun clearingEmptiesTheQueue() = runTest {
        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L)))

        queue.clear()

        assertTrue(pendingIds().isEmpty())
    }

    // ----- Migration ---------------------------------------------------------

    @Test
    fun theMigrationAddsTheQueueWithoutTouchingTheCachedArticles() = runTest {
        // A version-1 database may already exist on a device: destroying it
        // would erase the article cache, hence all offline-readable content
        // (SPECS.md §5.2). The migration is exercised by hand because the
        // project lacks `androidx.room:room-testing`.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(VersionOneSchema)
                .build(),
        )
        val db = helper.writableDatabase
        try {
            MIGRATION_1_2.migrate(db)

            assertEquals(1L, db.query("SELECT id FROM articles").useSingleLong())
            assertEquals(0L, db.query("SELECT COUNT(*) FROM pending_marks").useSingleLong())
        } finally {
            helper.close()
        }
    }

    /** Reproduces version 1 as `app/schemas/…/1.json` describes it, with one row to preserve. */
    private object VersionOneSchema : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `articles` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`url` TEXT, `published_at_epoch_seconds` INTEGER NOT NULL, `summary` TEXT NOT NULL, " +
                    "`image_url` TEXT, `author` TEXT, `feed_id` TEXT NOT NULL, `feed_title` TEXT NOT NULL, " +
                    "`is_read` INTEGER NOT NULL, `cached_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "INSERT INTO articles VALUES (1, 'Un titre', NULL, 1700000000, 'Extrait', " +
                    "NULL, NULL, 'feed/7', 'Le flux', 0, 1000000)",
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}

private fun android.database.Cursor.useSingleLong(): Long = use {
    moveToFirst()
    getLong(0)
}
