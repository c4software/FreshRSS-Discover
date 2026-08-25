package fr.vbrosseau.freshrssdiscover.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** Larger than what the tests write: the limit is never what they exercise. */
private const val LARGE_LIMIT = 100

/** Slightly past the seven-day threshold (SPECS.md §8, question 3). */
private val BEYOND_MAX_AGE = 8.days

/**
 * The purge, from the cache-maintenance side.
 *
 * What matters here is not the deletion but what it spares: an unread
 * article, and above all a read article whose mark has not yet reached the
 * server. Purging the latter would make an article the user has read
 * reappear in the feed as never read, with nothing in the app to signal it.
 */
@RunWith(RobolectricTestRunner::class)
class CacheMaintenanceTest {
    // In-memory database: the real SQLite engine, the only thing that
    // actually runs the `pending_marks` subquery carrying the guarantee.
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
     * Waits for the purge actually launched, not for the virtual scheduler:
     * the Room query hops to a real thread, which `advanceUntilIdle` does not
     * follow, and on a slow machine the assertion ran before the deletion
     * (seen on CI, v1.16.1).
     */
    private suspend fun TestScope.awaitPurge() {
        coroutineContext[Job]?.children?.forEach { it.join() }
    }

    /**
     * The ids still in the database, read articles included.
     *
     * The cache flow only returns unread articles (what the screen shows),
     * while a purge is judged on what is actually stored. Hence the direct
     * DAO read here, the only place in the repository that needs it.
     */
    private suspend fun cachedIds(): List<Long> =
        cache.observeArticles(LARGE_LIMIT).first().map { it.id.value }

    @Test
    fun theStartupPurgeRemovesReadArticlesPastTheThreshold() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        clock.advanceBy(BEYOND_MAX_AGE.inWholeMilliseconds)

        maintenance().purgeExpiredInBackground()
        awaitPurge()

        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun theStartupPurgeSparesReadArticlesYoungerThanTheThreshold() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        clock.advanceBy(CacheRepository.MaxAge.inWholeMilliseconds - 1)

        maintenance().purgeExpiredInBackground()
        awaitPurge()

        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun theStartupPurgeNeverRemovesAnUnreadArticle() = runTest {
        // SPECS.md §5.4: unread articles are the very content of the app.
        cache.save(listOf(article(id = 1L, isRead = false)))
        clock.advanceBy(365.days.inWholeMilliseconds)

        maintenance().purgeExpiredInBackground()
        awaitPurge()

        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun theStartupPurgeNeverRemovesAMarkNotYetTransmitted() = runTest {
        // The expensive case: offline longer than the threshold. Purging the
        // article would take the local "already read" memory with it
        // (`upsertPreservingLocalReadState` reads it from this table), and
        // the next refresh would bring it back as unread.
        cache.save(listOf(article(id = 1L, isRead = true)))
        queue.enqueue(listOf(ArticleId(1L)))
        clock.advanceBy(365.days.inWholeMilliseconds)

        maintenance().purgeExpiredInBackground()
        awaitPurge()

        assertEquals(listOf(1L), cachedIds())
    }

    @Test
    fun anArticleBecomesPurgeableOnceItsMarkIsAcknowledged() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        queue.enqueue(listOf(ArticleId(1L)))
        clock.advanceBy(BEYOND_MAX_AGE.inWholeMilliseconds)
        queue.acknowledge(listOf(ArticleId(1L)))

        maintenance().purgeExpiredInBackground()
        awaitPurge()

        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun theManualPurgeRemovesReadArticlesWithoutWaitingForTheThreshold() = runTest {
        // The manual purge is the same rule without the age condition: the
        // article just entered the cache and is removed anyway.
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

        // Three articles kept, one purgeable: the unread one is out of
        // reach, and so is the pending mark.
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
        // The displayed count must follow the action: it is the only
        // feedback the user gets, since the purge asks no confirmation.
        cache.save(listOf(article(id = 1L, isRead = true), article(id = 2L, isRead = false)))
        val maintenance = maintenance()

        maintenance.purgeReadArticles()

        assertEquals(
            CacheStatus(articleCount = 1, purgeableCount = 0),
            maintenance.observeCacheStatus().first(),
        )
    }
}
