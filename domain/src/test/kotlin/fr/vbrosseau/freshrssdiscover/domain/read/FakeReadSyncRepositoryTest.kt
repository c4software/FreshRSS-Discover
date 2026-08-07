package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Le faux servira à éprouver l'écran, qui ne doit jamais parler de réseau. Ces
 * tests fixent ce sur quoi cet écran s'appuiera : marquer n'échoue pas, les
 * lots restent observables un par un, et l'issue de la transmission est un
 * verdict pilotable.
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
    fun theProgrammedOutcomeIsWhatFlushReturns() =
        runTest {
            repository.nextOutcome = ReadSyncOutcome.Deferred(transmittedCount = 2)

            assertEquals(ReadSyncOutcome.Deferred(transmittedCount = 2), repository.flush())
            assertEquals(1, repository.flushCallCount)
        }

    @Test
    fun aFlushWithNothingPendingReportsNothingTransmitted() =
        runTest {
            assertEquals(ReadSyncOutcome.Synchronized(transmittedCount = 0), repository.flush())
        }

    @Test
    fun aPartialTransmissionIsNotTheSameAsACompleteOne() {
        // Deferred et Synchronized peuvent porter le même compte : c'est le
        // verdict qui distingue « tout est parti » de « le reste attend ».
        assertNotEquals<ReadSyncOutcome>(
            ReadSyncOutcome.Synchronized(transmittedCount = 2),
            ReadSyncOutcome.Deferred(transmittedCount = 2),
        )
        assertEquals(ReadSyncOutcome.SessionLost, ReadSyncOutcome.SessionLost)
    }

    @Test
    fun clearingResetsThePendingCount() =
        runTest {
            repository.pendingCount.value = 3
            assertEquals(3, repository.observePendingCount().first())

            repository.clearPending()

            assertEquals(0, repository.observePendingCount().first())
            assertEquals(1, repository.clearPendingCallCount)
        }
}
