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
    // Base en mémoire : le cache s'éprouve avec le vrai moteur SQLite, seul
    // capable de révéler une contrainte de clé ou une requête invalide qu'un
    // faux DAO laisserait passer.
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
        // Un flux mal formé produit réellement ces articles (SPECS.md §4.7) :
        // une chaîne vide au lieu de `null` les rendrait cliquables vers rien.
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
        // Le marquage part hors ligne et n'est transmis qu'au retour du réseau
        // (SPECS.md §5.2) : entre-temps le serveur décrit toujours l'article
        // comme non lu. L'écraser le ferait ressurgir dans le flux.
        cache.save(listOf(article(id = 1L, isRead = true)))

        cache.save(listOf(article(id = 1L, isRead = false)))

        // Il reste affiché — le flux du lancement garde les lus (SPECS.md §5.1)
        // — et il reste lu : c'est lui qui porte la mémoire du « déjà lu » tant
        // que le serveur l'ignore.
        assertTrue(cache.observeArticles(LARGE_LIMIT).first().single().isRead)
    }

    @Test
    fun anArticleReadElsewhereBecomesReadLocally() = runTest {
        // Le sens inverse doit rester possible : lu dans l'interface web ou sur
        // un autre appareil, l'article ne doit pas rester en tête du flux ici.
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
        // SPECS.md §5.4 : les non lus sont le contenu même de l'application.
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
        // Déconnexion (SPECS.md §3.5) : aucune trace du contenu d'un compte ne
        // doit survivre sur l'appareil.
        cache.save(listOf(article(id = 1L, isRead = true), article(id = 2L)))

        cache.clear()

        assertTrue(cache.observeArticles(LARGE_LIMIT).first().isEmpty())
    }

    @Test
    fun anEmptyCacheObservesAnEmptyList() = runTest {
        assertTrue(cache.observeArticles(LARGE_LIMIT).first().isEmpty())
    }
    // ----- Ce qu'il reste à lire (SPECS.md §4.9) ------------------------------

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
        // Le cas qui décide s'il y a une notification ou non : le filtre doit
        // être dans la requête, sans quoi une pile entièrement lue rapporterait
        // deux cents lignes pour n'en garder aucune.
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
     * Les identifiants **stockés**, lus compris, lus en SQL brut.
     *
     * Le flux du cache ne rend que les non-lus — c'est ce que l'écran affiche —
     * alors que la purge et la mémoire du « déjà lu » se jugent sur ce qui
     * reste en base. La requête est faite ici plutôt qu'ajoutée au DAO : le
     * code de production n'a pas à porter une méthode que seuls les tests
     * appellent (AGENTS.md §2).
     */
    private fun storedIds(): List<Long> =
        database.openHelper.readableDatabase
            .query("SELECT id FROM articles ORDER BY published_at_epoch_seconds DESC, id DESC")
            .use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
}
