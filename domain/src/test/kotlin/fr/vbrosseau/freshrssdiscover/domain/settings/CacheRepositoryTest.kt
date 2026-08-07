package fr.vbrosseau.freshrssdiscover.domain.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.days

/**
 * Le seuil de purge ne se constate nulle part à l'usage : trop court, il fait
 * disparaître le passé du flux entre deux lancements ; trop long, il laisse le
 * cache enfler. Aucun des deux ne lève d'erreur. Il est donc fixé ici, et vérifié.
 */
class CacheRepositoryTest {
    @Test
    fun theAutomaticPurgeKeepsReadArticlesForOneWeek() {
        // SPECS.md §8, question 3 : tranchée à 7 jours.
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
        // L'écran observe cet état : deux valeurs égales doivent l'être, sinon
        // `StateFlow` réémettrait à chaque lecture de la base et l'écran
        // clignoterait.
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
        // `toString` est ce qu'affiche un échec d'assertion : sans lui, un test
        // rouge ne dirait pas quel compteur a divergé.
        assertEquals("CacheStatus(articleCount=3, purgeableCount=1)", CacheStatus(3, 1).toString())
    }
}
