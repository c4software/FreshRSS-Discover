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

/** Article non lu présent dans la base **avant** la mise à jour. */
private val UnreadBeforeMigration: Article = article(
    id = 1L,
    title = "Article non lu",
    url = null,
    publishedAtEpochSeconds = 1_700_000_100L,
    summary = "Extrait 1",
    feed = feedRef(id = "feed/7", title = "Le flux"),
    isRead = false,
)

/** Article déjà lu, dont l'état ne doit pas reculer à la migration. */
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
 * Éprouve la mise à jour d'une installation **déjà en version 1**, telle
 * qu'elle se produit sur l'appareil d'un utilisateur.
 *
 * La différence avec le test de migration de `PendingMarkQueueTest` est le
 * chemin emprunté : celui-ci appelle `MIGRATION_1_2.migrate` directement, donc
 * il éprouve le SQL de la migration mais **pas** son branchement. Retirer
 * `addMigrations(MIGRATION_1_2)` de `DatabaseModule` le laisserait passer au
 * vert, alors que l'application deviendrait inutilisable après mise à jour.
 *
 * Ici, la base est écrite en version 1 sur le fichier même que le graphe
 * ouvrira ; c'est Room, à travers `DatabaseModule`, qui décide quoi faire.
 * L'ouverture valide de surcroît que la table créée par la migration est
 * **identique** à celle qu'une création directe en version 2 aurait produite :
 * Room compare le schéma réel à celui qu'il attend, et lève sinon.
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
     * L'ordre compte : la base en version 1 doit être écrite **avant** que quoi
     * que ce soit n'ouvre celle du graphe. Room n'ouvre qu'au premier accès,
     * l'injection seule ne suffit donc pas à figer le fichier.
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
        // Sans `addMigrations` dans DatabaseModule, cette ligne lèverait
        // `IllegalStateException` — exactement ce que subirait un utilisateur
        // qui met à jour l'application.
        assertEquals(EXPECTED_DATABASE_VERSION, database.openHelper.writableDatabase.version)
    }

    @Test
    fun theCachedArticlesSurviveTheMigrationUnchanged() = runTest {
        // Le cache est tout ce qui reste consultable hors ligne (SPECS.md §5.2)
        // : le perdre à une mise à jour viderait l'application au lancement.
        assertEquals(
            listOf(UnreadBeforeMigration, ReadBeforeMigration),
            articleCache.observeArticles(LARGE_LIMIT).first(),
        )
    }

    @Test
    fun theMigratedDatabaseIsUsableThroughTheRealDaos() = runTest {
        // Une base ouverte n'est pas une base utilisable : les requêtes du DAO
        // ne sont éprouvées qu'en les exécutant sur ce schéma-là.
        articleCache.markAsRead(listOf(UnreadBeforeMigration.id))
        pendingMarkQueue.enqueue(listOf(UnreadBeforeMigration.id))

        assertTrue(articleCache.observeArticles(LARGE_LIMIT).first().all(Article::isRead))
        assertEquals(listOf(ArticleId(1L)), pendingMarkQueue.pending(LARGE_LIMIT))
    }

    @Test
    fun thePurgeStillWorksOnAMigratedDatabase() = runTest {
        // La purge lit `cached_at_epoch_millis`, une colonne écrite par la
        // version 1 : c'est la colonne la plus exposée à une migration ratée.
        val purged = articleCache.purgeReadOlderThan(1.days)

        assertEquals(1, purged)
        assertEquals(listOf(UnreadBeforeMigration), articleCache.observeArticles(LARGE_LIMIT).first())
    }

    /**
     * Reproduit la version 1 telle que `app/schemas/…/1.json` la décrit, avec
     * deux articles à préserver.
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
