package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fake will exercise the screen, which must never involve the network.
 * These tests pin down what that screen will rely on: marking does not fail,
 * batches remain observable one by one, and every call is counted.
 */
class FakeReadSyncRepositoryTest {
    private val repository = FakeReadSyncRepository()

    private val first = ArticleId(1L)
    private val second = ArticleId(2L)

    @Test
    fun markingRecordsEachBatchAndCumulatesTheArticles() =
        runTest {
            // Scrolling produces one call per batch of seen articles:
            // observing only the last would miss a marking overwritten by the
            // next.
            repository.markAsRead(setOf(first))
            repository.markAsRead(setOf(second))

            assertEquals(listOf(setOf(first), setOf(second)), repository.markCalls)
            assertEquals(listOf(first, second), repository.markedIds)
        }

    @Test
    fun markingNothingIsStillAnObservableCall() =
        runTest {
            // The caller may mark an empty batch; the fake does not correct
            // it, otherwise a useless call from the screen would go unnoticed.
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
