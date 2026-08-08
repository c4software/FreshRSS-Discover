package fr.vbrosseau.freshrssdiscover.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** Un peu au-delà du seuil de sept jours (SPECS.md §8, question 3). */
private val BEYOND_MAX_AGE = 8.days

/**
 * La purge, vue de l'entretien du cache.
 *
 * Ce qui se joue ici n'est pas la suppression mais ce qu'elle **épargne** : un
 * article non lu, et surtout un article lu dont le marquage n'a pas encore
 * atteint le serveur. Purger le second ferait réapparaître dans le flux, comme
 * jamais lu, un article que l'utilisateur a lu — et rien dans l'application ne
 * le signalerait.
 */
@RunWith(RobolectricTestRunner::class)
class CacheMaintenanceTest {
    // Base en mémoire : le vrai moteur SQLite, seul à exécuter réellement la
    // sous-requête sur `pending_marks` qui porte toute la garantie.
    private val database = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()

    private val clock = FakeClock(nowMillis = 1_000_000L)
    private val cache = ArticleCache(database.articleDao(), clock)
    private val queue = PendingMarkQueue(database.pendingMarkDao(), clock)

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun TestScope.maintenance() = CacheMaintenance(cache, this)

    /**
     * Les identifiants **encore en base**, lus compris.
     *
     * Le flux du cache ne rend que les non-lus — c'est ce que l'écran affiche —
     * alors qu'une purge se juge sur ce qui reste réellement stocké. D'où la
     * lecture directe du DAO ici, seul endroit du dépôt qui en a besoin.
     */
    private fun cachedIds(): List<Long> =
        database.openHelper.readableDatabase
            .query("SELECT id FROM articles ORDER BY published_at_epoch_seconds DESC, id DESC")
            .use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }

    @Test
    fun theStartupPurgeRemovesReadArticlesPastTheThreshold() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        clock.advanceBy(BEYOND_MAX_AGE.inWholeMilliseconds)

        maintenance().purgeExpiredInBackground()
        advanceUntilIdle()

        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun theStartupPurgeSparesReadArticlesYoungerThanTheThreshold() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        clock.advanceBy(CacheRepository.MaxAge.inWholeMilliseconds - 1)

        maintenance().purgeExpiredInBackground()
        advanceUntilIdle()

        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun theStartupPurgeNeverRemovesAnUnreadArticle() = runTest {
        // SPECS.md §5.4 : les non lus sont le contenu même de l'application.
        cache.save(listOf(article(id = 1L, isRead = false)))
        clock.advanceBy(365.days.inWholeMilliseconds)

        maintenance().purgeExpiredInBackground()
        advanceUntilIdle()

        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun theStartupPurgeNeverRemovesAMarkNotYetTransmitted() = runTest {
        // Le cas qui coûte cher : hors ligne plus longtemps que le seuil.
        // Purger l'article emporterait la mémoire locale du « déjà lu » —
        // `upsertPreservingLocalReadState` la lit dans cette table — et le
        // prochain rafraîchissement le ferait réapparaître comme non lu.
        cache.save(listOf(article(id = 1L, isRead = true)))
        queue.enqueue(listOf(ArticleId(1L)))
        clock.advanceBy(365.days.inWholeMilliseconds)

        maintenance().purgeExpiredInBackground()
        advanceUntilIdle()

        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun anArticleBecomesPurgeableOnceItsMarkIsAcknowledged() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        queue.enqueue(listOf(ArticleId(1L)))
        clock.advanceBy(BEYOND_MAX_AGE.inWholeMilliseconds)
        queue.acknowledge(listOf(ArticleId(1L)))

        maintenance().purgeExpiredInBackground()
        advanceUntilIdle()

        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun theManualPurgeRemovesReadArticlesWithoutWaitingForTheThreshold() = runTest {
        // La purge manuelle est la même règle sans la condition d'ancienneté :
        // l'article vient d'entrer dans le cache et part quand même.
        cache.save(listOf(article(id = 1L, isRead = true), article(id = 2L, isRead = true)))

        assertEquals(2, maintenance().purgeReadArticles())
        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun theManualPurgeNeverRemovesAnUnreadArticle() = runTest {
        cache.save(listOf(article(id = 1L, isRead = false), article(id = 2L, isRead = true)))

        assertEquals(1, maintenance().purgeReadArticles())
        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun theManualPurgeNeverRemovesAMarkNotYetTransmitted() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true), article(id = 2L, isRead = true)))
        queue.enqueue(listOf(ArticleId(1L)))

        assertEquals(1, maintenance().purgeReadArticles())
        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun theStatusCountsWhatIsCachedAndWhatAPurgeWouldRemove() = runTest {
        cache.save(
            listOf(
                article(id = 1L, isRead = true),
                article(id = 2L, isRead = true),
                article(id = 3L, isRead = false),
            ),
        )
        queue.enqueue(listOf(ArticleId(2L)))

        // Trois articles conservés, un seul supprimable : le non lu est hors
        // d'atteinte, et le marquage en attente aussi.
        assertEquals(
            CacheStatus(articleCount = 3, purgeableCount = 1),
            maintenance().observeCacheStatus().first(),
        )
    }

    @Test
    fun anEmptyCacheReportsNothingToShowAndNothingToPurge() = runTest {
        assertEquals(CacheStatus.Empty, maintenance().observeCacheStatus().first())
    }

    @Test
    fun theStatusFallsAfterAManualPurge() = runTest {
        // Le chiffre affiché doit suivre le geste : c'est le seul retour que
        // l'utilisateur obtient, la purge ne demandant pas de confirmation.
        cache.save(listOf(article(id = 1L, isRead = true), article(id = 2L, isRead = false)))
        val maintenance = maintenance()

        maintenance.purgeReadArticles()

        assertEquals(
            CacheStatus(articleCount = 1, purgeableCount = 0),
            maintenance.observeCacheStatus().first(),
        )
    }
}
