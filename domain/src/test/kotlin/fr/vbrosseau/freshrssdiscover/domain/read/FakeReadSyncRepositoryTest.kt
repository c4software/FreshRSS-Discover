package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Le faux servira à éprouver l'écran, qui ne doit jamais parler de réseau. Ces
 * tests fixent ce sur quoi cet écran s'appuiera : marquer n'échoue pas, les
 * lots restent observables un par un, et chaque appel se compte.
 */
class FakeReadSyncRepositoryTest {
    private val repository = FakeReadSyncRepository()

    private val first = ArticleId(1L)
    private val second = ArticleId(2L)

    @Test
    fun markingRecordsEachBatchAndCumulatesTheArticles() =
        runTest {
            // Le défilement produit un appel par lot d'articles vus : n'observer
            // que le dernier laisserait passer un marquage écrasé par le suivant.
            repository.markAsRead(setOf(first))
            repository.markAsRead(setOf(second))

            assertEquals(listOf(setOf(first), setOf(second)), repository.markCalls)
            assertEquals(listOf(first, second), repository.markedIds)
        }

    @Test
    fun markingNothingIsStillAnObservableCall() =
        runTest {
            // L'appelant a le droit de marquer un lot vide ; le faux ne le corrige
            // pas, sinon un appel inutile de l'écran passerait inaperçu.
            repository.markAsRead(emptySet())

            assertEquals(listOf(emptySet()), repository.markCalls)
        }

    @Test
    fun everyForcedTransmissionIsCounted() =
        runTest {
            repository.flush()
            repository.flush()

            assertEquals(2, repository.flushCallCount)
        }

    @Test
    fun abandoningThePendingMarksIsCounted() =
        runTest {
            repository.clearPending()

            assertEquals(1, repository.clearPendingCallCount)
        }
}
