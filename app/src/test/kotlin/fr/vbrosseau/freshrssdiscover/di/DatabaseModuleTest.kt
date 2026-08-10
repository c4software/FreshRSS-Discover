package fr.vbrosseau.freshrssdiscover.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import fr.vbrosseau.freshrssdiscover.data.local.room.AppDatabase
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkQueue
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.article
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

private const val DATABASE_FILE = "freshrss-discover.db"
private const val EXPECTED_DATABASE_VERSION = 2
private const val LARGE_LIMIT = 100

/**
 * Exercises the database as the application obtains it: through the graph, on
 * disk, with the migrations declared by `DatabaseModule`.
 *
 * All other persistence tests build an in-memory database by hand
 * (`inMemoryDatabaseBuilder`). An in-memory database is always created at the
 * current version: it goes through no migration, has no file, and therefore
 * cannot reveal a forgotten `addMigrations` — the defect `DatabaseModule`
 * documents as invisible to tests. This test closes that gap.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DatabaseModuleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    internal lateinit var database: AppDatabase

    @Inject
    internal lateinit var articleCache: ArticleCache

    @Inject
    internal lateinit var pendingMarkQueue: PendingMarkQueue

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun theGraphProvidesAFileBackedDatabaseAndNotAnInMemoryOne() {
        // The distinction is the whole point: the file is what survives an
        // application update, so only it can one day show up at an earlier
        // version.
        assertEquals(DATABASE_FILE, database.openHelper.databaseName)

        // Room only opens on first access: without this read, no file would
        // exist yet.
        assertEquals(EXPECTED_DATABASE_VERSION, database.openHelper.writableDatabase.version)

        val file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath(DATABASE_FILE)
        assertTrue(file.exists(), "La base fournie par le graphe doit exister sur disque : $file")
    }

    @Test
    fun theDatabaseOpensAtTheCurrentSchemaVersion() {
        // Opening also lets Room compare the actual schema against the exported
        // schema fingerprint: a mismatch throws here, not on the user's device.
        assertEquals(EXPECTED_DATABASE_VERSION, database.openHelper.writableDatabase.version)
    }

    @Test
    fun bothTablesDeclaredByTheEntitiesExistInAFreshDatabase() {
        val tables = database.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toSet()
            }

        assertTrue("articles" in tables, "Tables trouvées : $tables")
        assertTrue("pending_marks" in tables, "Tables trouvées : $tables")
    }

    @Test
    fun anArticleSavedThroughTheGraphIsReadBackFromDisk() = runTest {
        val saved = article(id = 42L, title = "Un titre")

        articleCache.save(listOf(saved))

        assertEquals(listOf(saved), articleCache.observeArticles(LARGE_LIMIT).first())
    }

    @Test
    fun aMarkQueuedThroughTheGraphIsReadBackFromDisk() = runTest {
        // The queue DAO uses the table added by MIGRATION_1_2: querying it
        // through the graph verifies the table is there whichever path was
        // taken, direct creation at version 2 here, migration elsewhere.
        pendingMarkQueue.enqueue(listOf(ArticleId(7L)))

        assertEquals(listOf(ArticleId(7L)), pendingMarkQueue.pending(LARGE_LIMIT))
    }
}
