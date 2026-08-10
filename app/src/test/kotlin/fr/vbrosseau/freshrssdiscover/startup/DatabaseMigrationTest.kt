package fr.vbrosseau.freshrssdiscover.startup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import fr.vbrosseau.freshrssdiscover.data.local.room.AppDatabase
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkQueue
import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.feed.feedRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

private const val DATABASE_FILE = "freshrss-discover.db"
private const val EXPECTED_DATABASE_VERSION = 2
private const val LARGE_LIMIT = 100

/** Unread article present in the database before the upgrade. */
private val UnreadBeforeMigration: Article = article(
    id = 1L,
    title = "Article non lu",
    url = null,
    publishedAtEpochSeconds = 1_700_000_100L,
    summary = "Extrait 1",
    feed = feedRef(id = "feed/7", title = "Le flux"),
    isRead = false,
)

/** Already-read article whose state must not regress during the migration. */
private val ReadBeforeMigration: Article = article(
    id = 2L,
    title = "Article déjà lu",
    url = null,
    publishedAtEpochSeconds = 1_700_000_000L,
    summary = "Extrait 2",
    feed = feedRef(id = "feed/7", title = "Le flux"),
    isRead = true,
)

/**
 * Exercises upgrading an installation already at version 1, as it happens on a
 * user's device.
 *
 * Unlike the migration test in `PendingMarkQueueTest`, which calls
 * `MIGRATION_1_2.migrate` directly and therefore exercises the migration SQL
 * but not its wiring, this test writes a version-1 database onto the very file
 * the graph will open; Room, through `DatabaseModule`, decides what to do.
 * Removing `addMigrations(MIGRATION_1_2)` from `DatabaseModule` would leave the
 * direct test green while making the app unusable after an update.
 *
 * Opening the database also validates that the migrated table is identical to
 * one created directly at version 2: Room compares the actual schema with the
 * expected one and throws otherwise.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DatabaseMigrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    internal lateinit var database: AppDatabase

    @Inject
    internal lateinit var articleCache: ArticleCache

    @Inject
    internal lateinit var pendingMarkQueue: PendingMarkQueue

    /**
     * Order matters: the version-1 database must be written before anything
     * opens the graph's database. Room only opens on first access, so injection
     * alone does not pin the file.
     */
    @Before
    fun writeVersionOneDatabaseThenInject() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_FILE)
                .callback(VersionOneSchema)
                .build(),
        )
        val writtenVersion = helper.use { it.writableDatabase.version }
        assertEquals(1, writtenVersion, "La base préexistante doit être en version 1")

        hiltRule.inject()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anExistingVersionOneDatabaseIsMigratedInsteadOfRefusingToOpen() {
        // Without `addMigrations` in DatabaseModule this line would throw
        // `IllegalStateException`, exactly what a user updating the app
        // would hit.
        assertEquals(EXPECTED_DATABASE_VERSION, database.openHelper.writableDatabase.version)
    }

    @Test
    fun theCachedArticlesSurviveTheMigrationUnchanged() = runTest {
        // The cache is all that remains readable offline (SPECS.md §5.2):
        // losing it on an update would empty the app at launch.
        assertEquals(
            listOf(UnreadBeforeMigration, ReadBeforeMigration),
            articleCache.observeArticles(LARGE_LIMIT).first(),
        )
    }

    @Test
    fun theMigratedDatabaseIsUsableThroughTheRealDaos() = runTest {
        // An open database is not necessarily a usable one: the DAO queries
        // are only exercised by running them against this exact schema.
        articleCache.markAsRead(listOf(UnreadBeforeMigration.id))
        pendingMarkQueue.enqueue(listOf(UnreadBeforeMigration.id))

        assertTrue(articleCache.observeArticles(LARGE_LIMIT).first().all(Article::isRead))
        assertEquals(listOf(ArticleId(1L)), pendingMarkQueue.pending(LARGE_LIMIT))
    }

    @Test
    fun thePurgeStillWorksOnAMigratedDatabase() = runTest {
        // The purge reads `cached_at_epoch_millis`, a column written by
        // version 1: the column most exposed to a botched migration.
        val purged = articleCache.purgeReadOlderThan(1.days)

        assertEquals(1, purged)
        assertEquals(listOf(UnreadBeforeMigration), articleCache.observeArticles(LARGE_LIMIT).first())
    }

    /**
     * Reproduces version 1 as described by `app/schemas/…/1.json`, with two
     * articles to preserve.
     */
    private object VersionOneSchema : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `articles` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`url` TEXT, `published_at_epoch_seconds` INTEGER NOT NULL, `summary` TEXT NOT NULL, " +
                    "`image_url` TEXT, `author` TEXT, `feed_id` TEXT NOT NULL, `feed_title` TEXT NOT NULL, " +
                    "`is_read` INTEGER NOT NULL, `cached_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "INSERT INTO articles VALUES (1, 'Article non lu', NULL, 1700000100, 'Extrait 1', " +
                    "NULL, NULL, 'feed/7', 'Le flux', 0, 1000000)",
            )
            db.execSQL(
                "INSERT INTO articles VALUES (2, 'Article déjà lu', NULL, 1700000000, 'Extrait 2', " +
                    "NULL, NULL, 'feed/7', 'Le flux', 1, 1000000)",
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
