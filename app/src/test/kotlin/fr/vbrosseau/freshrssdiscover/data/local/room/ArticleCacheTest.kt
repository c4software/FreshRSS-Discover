package fr.vbrosseau.freshrssdiscover.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.feed.feedRef
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

private const val LARGE_LIMIT = 100

@RunWith(RobolectricTestRunner::class)
class ArticleCacheTest {
    // In-memory database: the cache is exercised against the real SQLite
    // engine, the only thing that can reveal a key constraint or invalid
    // query a fake DAO would let through.
    private val database = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()

    private val clock = FakeClock(nowMillis = 1_000_000L)
    private val cache = ArticleCache(database.articleDao(), clock)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anArticleIsRestoredExactlyAsItWasSaved() = runTest {
        val saved = article(
            id = 42L,
            title = "Un titre",
            url = "https://exemple.org/42",
            publishedAtEpochSeconds = 1_700_000_000L,
            summary = "Un extrait.",
            imageUrl = "https://exemple.org/42.png",
            author = "Alice",
            feed = feedRef(id = "feed/7", title = "Le flux"),
        )

        cache.save(listOf(saved))

        assertEquals(listOf(saved), cache.observeArticles(LARGE_LIMIT).first())
    }

    @Test
    fun anArticleWithoutUrlImageOrAuthorKeepsThoseFieldsAbsent() = runTest {
        // A malformed feed really produces such articles (SPECS.md §4.7): an
        // empty string instead of `null` would make them clickable to nowhere.
        cache.save(listOf(article(id = 1L, url = null, imageUrl = null, author = null)))

        val restored = cache.observeArticles(LARGE_LIMIT).first().single()
        assertNull(restored.url)
        assertNull(restored.imageUrl)
        assertNull(restored.author)
    }

    @Test
    fun savingAnArticleAlreadyPresentUpdatesIt() = runTest {
        cache.save(listOf(article(id = 1L, title = "Titre initial")))

        cache.save(listOf(article(id = 1L, title = "Titre corrigé")))

        val restored = cache.observeArticles(LARGE_LIMIT).first().single()
        assertEquals("Titre corrigé", restored.title)
    }

    @Test
    fun anArticleReadLocallyStaysReadEvenIfTheServerStillReportsItUnread() = runTest {
        // Marks happen offline and are only sent when the network returns
        // (SPECS.md §5.2): meanwhile the server still describes the article
        // as unread. Overwriting it would bring it back into the feed.
        cache.save(listOf(article(id = 1L, isRead = true)))

        cache.save(listOf(article(id = 1L, isRead = false)))

        // Still displayed (the launch feed keeps read articles, SPECS.md
        // §5.1) and still read: it carries the "already read" memory while
        // the server is unaware.
        assertTrue(cache.observeArticles(LARGE_LIMIT).first().single().isRead)
    }

    @Test
    fun savingAnEmptyPageChangesNothing() = runTest {
        // The reader who has read everything: the server returns an empty
        // page, and the upsert must neither fail (`IN ()` is not valid SQL)
        // nor touch what is there.
        cache.save(listOf(article(id = 1L, isRead = true)))

        cache.save(emptyList())

        assertTrue(cache.observeArticles(LARGE_LIMIT).first().single().isRead)
    }

    @Test
    fun anArticleReadElsewhereBecomesReadLocally() = runTest {
        // The reverse direction must stay possible: read in the web UI or on
        // another device, the article must not stay atop the feed here.
        cache.save(listOf(article(id = 1L, isRead = false)))

        cache.save(listOf(article(id = 1L, isRead = true)))

        assertTrue(cache.observeArticles(LARGE_LIMIT).first().single().isRead)
    }

    @Test
    fun observingReturnsTheMostRecentArticlesWithinTheRequestedLimit() = runTest {
        cache.save(
            listOf(
                article(id = 1L, publishedAtEpochSeconds = 100L),
                article(id = 2L, publishedAtEpochSeconds = 300L),
                article(id = 3L, publishedAtEpochSeconds = 200L),
            ),
        )

        val visible = cache.observeArticles(limit = 2).first()

        assertEquals(listOf(2L, 3L), visible.map { it.id.value })
    }

    @Test
    fun purgingRemovesReadArticlesOldEnough() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        clock.advanceBy(8.days.inWholeMilliseconds)

        assertEquals(1, cache.purgeReadOlderThan(7.days))
        assertTrue(cache.observeArticles(LARGE_LIMIT).first().isEmpty())
    }

    @Test
    fun purgingSparesReadArticlesStillRecent() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true)))
        clock.advanceBy(1.days.inWholeMilliseconds)

        assertEquals(0, cache.purgeReadOlderThan(7.days))
        assertEquals(listOf(1L), storedIds())
    }

    @Test
    fun purgingNeverRemovesUnreadArticlesHoweverOldTheyAre() = runTest {
        // SPECS.md §5.4: unread articles are the very content of the app.
        cache.save(listOf(article(id = 1L, isRead = false)))
        clock.advanceBy(365.days.inWholeMilliseconds)

        assertEquals(0, cache.purgeReadOlderThan(7.days))
        assertEquals(listOf(1L), cache.observeArticles(LARGE_LIMIT).first().map { it.id.value })
    }

    @Test
    fun purgingKeepsWhatItMustAndDropsTheRest() = runTest {
        cache.save(listOf(article(id = 1L, isRead = true), article(id = 2L, isRead = false)))
        clock.advanceBy(8.days.inWholeMilliseconds)
        cache.save(listOf(article(id = 3L, isRead = true)))

        assertEquals(1, cache.purgeReadOlderThan(7.days))

        assertEquals(listOf(3L, 2L), storedIds().sortedDescending())
    }

    @Test
    fun clearingEmptiesTheCache() = runTest {
        // Logout (SPECS.md §3.5): no trace of an account's content may
        // survive on the device.
        cache.save(listOf(article(id = 1L, isRead = true), article(id = 2L)))

        cache.clear()

        assertTrue(cache.observeArticles(LARGE_LIMIT).first().isEmpty())
    }

    @Test
    fun anEmptyCacheObservesAnEmptyList() = runTest {
        assertTrue(cache.observeArticles(LARGE_LIMIT).first().isEmpty())
    }
    // ----- What remains to read (SPECS.md §4.9) -------------------------------

    @Test
    fun onlyUnreadArticlesAreOfferedToTheReminder() = runTest {
        cache.save(
            listOf(
                article(id = 1L, title = "Lu", isRead = true),
                article(id = 2L, title = "À lire"),
            ),
        )

        assertEquals(listOf("À lire"), cache.unreadArticles(LARGE_LIMIT).map { it.title })
    }

    @Test
    fun theReminderSeesTheMostRecentArticlesFirst() = runTest {
        cache.save(
            listOf(
                article(id = 1L, title = "Ancien", publishedAtEpochSeconds = 1_000L),
                article(id = 2L, title = "Récent", publishedAtEpochSeconds = 9_000L),
            ),
        )

        assertEquals(listOf("Récent", "Ancien"), cache.unreadArticles(LARGE_LIMIT).map { it.title })
    }

    @Test
    fun theLimitIsAppliedBySqliteAndNotAfterwards() = runTest {
        cache.save(List(20) { article(id = it.toLong(), publishedAtEpochSeconds = it.toLong()) })

        assertEquals(3, cache.unreadArticles(limit = 3).size)
    }

    @Test
    fun aFullyReadCacheOffersNothingRatherThanEverything() = runTest {
        // The case deciding whether a notification fires: the filter must be
        // in the query, otherwise a fully read pile would fetch two hundred
        // rows to keep none.
        cache.save(List(5) { article(id = it.toLong(), isRead = true) })

        assertTrue(cache.unreadArticles(LARGE_LIMIT).isEmpty())
    }

    @Test
    fun anArticleMarkedReadLeavesTheReminderImmediately() = runTest {
        cache.save(listOf(article(id = 1L, title = "À lire"), article(id = 2L, title = "Aussi")))

        cache.markAsRead(listOf(ArticleId(1L)))

        assertEquals(listOf("Aussi"), cache.unreadArticles(LARGE_LIMIT).map { it.title })
    }

    /**
     * Stored ids, including read articles.
     *
     * Read through the cache flow (which returns read articles since
     * `GOAL-015-T08`) rather than a direct query: the flow waits for Room's
     * invalidation, whereas a synchronous read can outrun a purge running in
     * the background. Observed as flaky in CI.
     */
    private suspend fun storedIds(): List<Long> =
        cache.observeArticles(LARGE_LIMIT).first().map { it.id.value }
}
