package fr.vbrosseau.freshrssdiscover.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.flow.first
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
    // Base en mémoire : la file s'éprouve avec le vrai moteur SQLite, seul
    // capable de révéler une contrainte de clé primaire ou une requête invalide
    // qu'un faux DAO laisserait passer.
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

    // ----- Mise en file ------------------------------------------------------

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
        // Le flux repasse sur les mêmes articles au gré du défilement. La file
        // décrit ce qui reste à dire au serveur, pas ce qui s'est passé à
        // l'écran : un doublon n'apporterait rien et ferait grossir la file.
        queue.enqueue(listOf(ArticleId(7L)))
        clock.advanceBy(START_MILLIS)

        queue.enqueue(listOf(ArticleId(7L), ArticleId(8L)))

        assertEquals(listOf(7L, 8L), pendingIds())
    }

    @Test
    fun anArticleSeenAgainKeepsItsOriginalPlaceInTheQueue() = runTest {
        // Repousser l'horodatage à chaque nouvelle vue ferait reculer sans fin
        // un article fréquemment revu : il pourrait ne jamais être transmis.
        queue.enqueue(listOf(ArticleId(7L)))
        clock.advanceBy(START_MILLIS)
        queue.enqueue(listOf(ArticleId(8L)))

        queue.enqueue(listOf(ArticleId(7L)))

        assertEquals(listOf(7L), pendingIds(limit = 1))
    }

    // ----- Lecture par lots --------------------------------------------------

    @Test
    fun pendingReturnsTheOldestMarksFirstAndNoMoreThanTheLimit() = runTest {
        // L'envoi se fait par lots : une file accumulée hors ligne pendant des
        // jours ne doit pas partir en une seule requête démesurée.
        queue.enqueue(listOf(ArticleId(3L)))
        clock.advanceBy(START_MILLIS)
        queue.enqueue(listOf(ArticleId(1L)))
        clock.advanceBy(START_MILLIS)
        queue.enqueue(listOf(ArticleId(2L)))

        assertEquals(listOf(3L, 1L), pendingIds(limit = 2))
    }

    @Test
    fun readingTheQueueDoesNotEmptyIt() = runTest {
        // Le point central : retirer à la lecture perdrait le marquage dès
        // qu'une requête échoue, ce que cette file existe précisément pour
        // empêcher (SPECS.md §4.5).
        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L)))

        queue.pending(LARGE_LIMIT)

        assertEquals(listOf(1L, 2L), pendingIds())
    }

    // ----- Acquittement ------------------------------------------------------

    @Test
    fun acknowledgingRemovesOnlyWhatWasTransmitted() = runTest {
        // Un lot peut n'être transmis qu'en partie — une requête part, la
        // suivante échoue. Le reste doit survivre pour être rejoué.
        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L), ArticleId(3L)))

        queue.acknowledge(listOf(ArticleId(1L), ArticleId(3L)))

        assertEquals(listOf(2L), pendingIds())
    }

    @Test
    fun acknowledgingAnArticleAbsentFromTheQueueIsHarmless() = runTest {
        // Cas réel : deux tentatives de rejeu se recouvrent après un
        // redémarrage. Le second acquittement ne doit pas échouer.
        queue.enqueue(listOf(ArticleId(1L)))

        queue.acknowledge(listOf(ArticleId(1L), ArticleId(99L)))

        assertTrue(pendingIds().isEmpty())
    }

    // ----- Comptage et effacement --------------------------------------------

    @Test
    fun thePendingCountFollowsTheQueue() = runTest {
        assertEquals(0, queue.observePendingCount().first())

        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L)))
        assertEquals(2, queue.observePendingCount().first())

        queue.acknowledge(listOf(ArticleId(1L)))
        assertEquals(1, queue.observePendingCount().first())
    }

    @Test
    fun clearingEmptiesTheQueue() = runTest {
        queue.enqueue(listOf(ArticleId(1L), ArticleId(2L)))

        queue.clear()

        assertTrue(pendingIds().isEmpty())
        assertEquals(0, queue.observePendingCount().first())
    }

    // ----- Migration ---------------------------------------------------------

    @Test
    fun theMigrationAddsTheQueueWithoutTouchingTheCachedArticles() = runTest {
        // Une base en version 1 peut déjà exister sur un appareil : la détruire
        // effacerait le cache d'articles, donc tout le contenu consultable hors
        // ligne (SPECS.md §5.2). La migration est éprouvée à la main faute de
        // `androidx.room:room-testing` au projet — y ajouter une dépendance
        // sortirait du périmètre de cette tâche.
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

    /** Reproduit la version 1 telle que `app/schemas/…/1.json` la décrit, avec une ligne à préserver. */
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
