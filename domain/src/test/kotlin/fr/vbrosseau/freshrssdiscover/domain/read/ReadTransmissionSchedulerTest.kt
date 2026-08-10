package fr.vbrosseau.freshrssdiscover.domain.read

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Window used by these tests, independent of the domain's default value. */
private const val GROUPING_DELAY = 5_000L

/** Number of visibility samples covered by one window, at screen cadence. */
private const val FAST_MARKS = 5

/** Screen visibility sampling cadence. */
private const val FAST_MARK_INTERVAL = 200L

/** How far a late mark precedes the window's deadline. */
private const val LATE_MARK_OFFSET = 1_000L

/**
 * Verifies that grouping delays transmission without ever losing it
 * (SPECS.md §4.5).
 *
 * Time is entirely virtual: the window is measured to the millisecond, and
 * each boundary is probed from both sides, just before the deadline and on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadTransmissionSchedulerTest {
    /** Number of transmissions started, the only thing the window counts. */
    private var transmissionCount = 0

    /** In-flight transmissions and their maximum: how overlap is observed. */
    private var runningTransmissions = 0
    private var maxSimultaneousTransmissions = 0

    /** Holds the in-flight transmission, to reproduce a slow send. */
    private var suspender: CompletableDeferred<Unit>? = null

    private suspend fun transmit() {
        transmissionCount++
        runningTransmissions++
        maxSimultaneousTransmissions = maxOf(maxSimultaneousTransmissions, runningTransmissions)
        suspender?.await()
        runningTransmissions--
    }

    private fun TestScope.newScheduler() =
        ReadTransmissionScheduler(
            scope = backgroundScope,
            groupingDelayMillis = GROUPING_DELAY,
            transmit = ::transmit,
        )

    /**
     * Brings the window to its deadline.
     *
     * `advanceUntilIdle` would not work: the window lives in
     * `backgroundScope`, and the test scheduler does not advance time for
     * background work; it only runs it once the deadline is reached, hence
     * the final `runCurrent`.
     */
    private fun TestScope.elapseWindow() {
        advanceTimeBy(GROUPING_DELAY)
        runCurrent()
    }

    @Test
    fun schedulingTransmitsNothingBeforeTheGroupingDelay() =
        runTest {
            // The deferred half of SPECS.md §4.5: local marking is already
            // done when this window opens, so the network is in no hurry.
            val scheduler = newScheduler()

            scheduler.schedule()
            advanceTimeBy(GROUPING_DELAY)

            assertEquals(0, transmissionCount)
        }

    @Test
    fun schedulingTransmitsOnceTheGroupingDelayHasElapsed() =
        runTest {
            val scheduler = newScheduler()

            scheduler.schedule()
            advanceTimeBy(GROUPING_DELAY)
            runCurrent()

            assertEquals(1, transmissionCount)
            assertEquals(GROUPING_DELAY, currentTime)
        }

    @Test
    fun severalMarksWithinTheWindowProduceASingleTransmission() =
        runTest {
            // The scrolling case: a batch detected every 200 ms and a single
            // request at the end. Exactly what grouping exists for.
            val scheduler = newScheduler()

            repeat(FAST_MARKS) {
                scheduler.schedule()
                advanceTimeBy(FAST_MARK_INTERVAL)
            }
            elapseWindow()

            assertEquals(1, transmissionCount)
        }

    @Test
    fun aMarkArrivingDuringTheWindowDoesNotPostponeTheTransmission() =
        runTest {
            // The deadline is fixed, not sliding: otherwise continuous
            // scrolling would postpone the send indefinitely, and the only
            // chance to transmit would be when scrolling stops, often at app
            // close.
            val scheduler = newScheduler()

            scheduler.schedule()
            advanceTimeBy(GROUPING_DELAY - LATE_MARK_OFFSET)
            scheduler.schedule()
            advanceTimeBy(LATE_MARK_OFFSET)
            runCurrent()

            assertEquals(1, transmissionCount)
            assertEquals(GROUPING_DELAY, currentTime)
        }

    @Test
    fun aMarkArrivingAfterATransmissionOpensANewWindow() =
        runTest {
            val scheduler = newScheduler()

            scheduler.schedule()
            elapseWindow()
            scheduler.schedule()
            elapseWindow()

            assertEquals(2, transmissionCount)
        }

    @Test
    fun transmittingNowDoesNotWaitForTheGroupingDelay() =
        runTest {
            // The startup replay: there is nothing to group, only to catch up.
            val scheduler = newScheduler()

            scheduler.transmitNow()

            assertEquals(1, transmissionCount)
            assertEquals(0L, currentTime)
        }

    @Test
    fun transmittingNowConsumesTheOpenWindow() =
        runTest {
            // Otherwise the forced send would be followed by a second, empty
            // send when the abandoned window reached its deadline.
            val scheduler = newScheduler()

            scheduler.schedule()
            scheduler.transmitNow()
            elapseWindow()

            assertEquals(1, transmissionCount)
        }

    @Test
    fun aMarkArrivingDuringATransmissionIsTransmittedByTheNextWindow() =
        runTest {
            // The case where a mark could get lost: the in-flight transmission
            // may already have read the queue, so the mark needs its own
            // window.
            val scheduler = newScheduler()
            suspender = CompletableDeferred()
            scheduler.schedule()
            elapseWindow()

            scheduler.schedule()
            suspender?.complete(Unit)
            elapseWindow()

            assertEquals(2, transmissionCount)
        }

    @Test
    fun twoTransmissionsNeverOverlap() =
        runTest {
            // Two simultaneous sends would read the same queue before either
            // acknowledged it, sending the same articles twice.
            val scheduler = newScheduler()
            suspender = CompletableDeferred()
            scheduler.schedule()
            elapseWindow()

            val forced = backgroundScope.async { scheduler.transmitNow() }
            runCurrent()
            assertEquals(1, transmissionCount)

            suspender?.complete(Unit)
            forced.await()

            assertEquals(2, transmissionCount)
            assertEquals(1, maxSimultaneousTransmissions)
        }

    @Test
    fun cancellingDropsTheScheduledTransmission() =
        runTest {
            // Sign-out: the queue is abandoned, the window has nothing left
            // to tell the server.
            val scheduler = newScheduler()

            scheduler.schedule()
            scheduler.cancelScheduled()
            elapseWindow()

            assertEquals(0, transmissionCount)
        }

    @Test
    fun cancellingWithoutAnOpenWindowTransmitsNothing() =
        runTest {
            val scheduler = newScheduler()

            scheduler.cancelScheduled()
            elapseWindow()

            assertEquals(0, transmissionCount)
        }
}
