package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.days

/**
 * The purge threshold is nowhere observable in use: too short, the feed's
 * past disappears between launches; too long, the cache bloats. Neither
 * raises an error, so the value is fixed and verified here.
 */
class CacheRepositoryTest {
    @Test
    fun theAutomaticPurgeKeepsReadArticlesForOneWeek() {
        // SPECS.md §8, question 3: settled at 7 days.
        assertEquals(7.days, CacheRepository.MaxAge)
    }

    @Test
    fun anEmptyCacheHasNothingToShowAndNothingToPurge() {
        val (articles, purgeable) = CacheStatus.Empty

        assertEquals(0, articles)
        assertEquals(0, purgeable)
    }

    @Test
    fun twoStatusesDifferAsSoonAsOneOfTheirCountsDiffers() {
        // The screen observes this state: equal values must compare equal,
        // otherwise `StateFlow` would re-emit on every database read and the
        // screen would flicker.
        assertEquals(CacheStatus(articleCount = 3, purgeableCount = 1), CacheStatus(3, 1))
        assertNotEquals(CacheStatus(articleCount = 3, purgeableCount = 1), CacheStatus(3, 2))
        assertEquals(
            CacheStatus(3, 1).hashCode(),
            CacheStatus(3, 1).hashCode(),
        )
    }

    @Test
    fun purgingRemovesOnlyWhatWasPurgeable() =
        runTest {
            val repository = FakeCacheRepository(CacheStatus(articleCount = 10, purgeableCount = 4))

            assertEquals(4, repository.purgeReadArticles())

            assertEquals(CacheStatus(articleCount = 6, purgeableCount = 0), repository.current)
            assertEquals(1, repository.purgeCount)
        }

    @Test
    fun theStatusIsReadableAsText() {
        // `toString` is what an assertion failure prints: without it, a red
        // test would not say which counter diverged.
        assertEquals("CacheStatus(articleCount=3, purgeableCount=1)", CacheStatus(3, 1).toString())
    }
}
