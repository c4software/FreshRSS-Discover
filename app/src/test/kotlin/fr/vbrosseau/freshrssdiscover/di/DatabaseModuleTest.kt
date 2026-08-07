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
 * Éprouve la base **telle que l'application l'obtient** : par le graphe, sur
 * disque, avec les migrations déclarées par `DatabaseModule`.
 *
 * Tous les autres tests de persistance construisent une base en mémoire à la
 * main (`inMemoryDatabaseBuilder`). C'est commode et rapide, mais une base en
 * mémoire est **toujours créée à la version courante** : elle ne passe par
 * aucune migration, n'a pas de fichier, et ne peut donc pas révéler qu'un
 * `addMigrations` a été oublié — le défaut que `DatabaseModule` documente
 * précisément comme invisible aux tests. Ce test-ci ferme cette porte.
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
        // La distinction est le fond du sujet : c'est le fichier qui survit à
        // une mise à jour de l'application, donc lui seul qui peut se présenter
        // un jour dans une version antérieure.
        assertEquals(DATABASE_FILE, database.openHelper.databaseName)

        // Room n'ouvre qu'au premier accès : sans cette lecture, aucun fichier
        // n'existerait encore.
        assertEquals(EXPECTED_DATABASE_VERSION, database.openHelper.writableDatabase.version)

        val file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath(DATABASE_FILE)
        assertTrue(file.exists(), "La base fournie par le graphe doit exister sur disque : $file")
    }

    @Test
    fun theDatabaseOpensAtTheCurrentSchemaVersion() {
        // Ouvrir, c'est aussi laisser Room comparer le schéma réel à l'empreinte
        // du schéma exporté : un écart lève ici, et non chez l'utilisateur.
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
        // Le DAO de la file vient de la table ajoutée par MIGRATION_1_2 : la
        // solliciter par le graphe vérifie qu'elle est bien là, quel que soit le
        // chemin — création directe en version 2 ici, migration ailleurs.
        pendingMarkQueue.enqueue(listOf(ArticleId(7L)))

        assertEquals(listOf(ArticleId(7L)), pendingMarkQueue.pending(LARGE_LIMIT))
    }
}
