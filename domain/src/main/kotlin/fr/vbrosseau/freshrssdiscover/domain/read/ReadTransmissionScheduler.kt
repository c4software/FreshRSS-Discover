package fr.vbrosseau.freshrssdiscover.domain.read

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Grouping delay for markings before transmission (SPECS.md §8, question 4).
 *
 * The value sits between two bounds:
 *
 * - lower bound: [ReadDetector] already requires 1 second of continuous
 *   visibility before declaring an article read, so at the fastest possible
 *   pace one article per second becomes read. A window shorter than that
 *   second would systematically close on a single article and produce exactly
 *   the per-article request SPECS.md §4.5 rules out;
 * - upper bound: the window is the time during which a marking is known only
 *   to the device. Nothing is lost (the queue survives closing and restart),
 *   but until transmitted the read is not visible from the web or another
 *   device. Reading and then quitting the app is the common case, and the
 *   user must not wait for the next launch for a read done ten seconds
 *   earlier.
 *
 * 5 seconds is five times the detection threshold: the screen samples
 * visibility every 200 ms, so one window groups up to 25 detected batches into
 * one request, for at most five articles, well under the data layer's batch of
 * 100. It also stays within the scale of a gesture: quitting the app within
 * five seconds of a read is possible but exceptional.
 */
private const val DEFAULT_GROUPING_DELAY_MILLIS = 5_000L

/**
 * Groups marking transmissions in time.
 *
 * Local marking does not go through here: it stays immediate, the optimistic
 * half of SPECS.md §4.5, and delaying it would make a just-read article
 * reappear on screen. Only the transmission is deferred.
 *
 * The window has a fixed deadline: a marking arriving during the wait joins
 * the current window without extending it. This must not be inverted:
 * continuous scrolling produces a batch every 200 ms, so a sliding window
 * would never close while the user reads. Transmission would only happen when
 * scrolling stops, often the very moment the app closes. A fixed deadline
 * bounds the wait to [groupingDelayMillis] whatever the user does.
 *
 * Time comes from `delay`, not `Clock`: this is a wait, not a timestamp, and
 * the virtual scheduler of `kotlinx-coroutines-test` makes it verifiable
 * without real waiting.
 */
class ReadTransmissionScheduler(
    private val scope: CoroutineScope,
    private val groupingDelayMillis: Long = DEFAULT_GROUPING_DELAY_MILLIS,
    private val transmit: suspend () -> Unit,
) {
    /** Guards [window]: a marking and a forced dispatch may arrive from different threads. */
    private val windowMutex = Mutex()

    /**
     * Serializes transmissions.
     *
     * Two simultaneous dispatches would read the same queue before either
     * acknowledged it, sending the same articles twice. The remote marking is
     * idempotent, but the request would be paid twice.
     */
    private val transmissionMutex = Mutex()

    /** Current window, or `null` when none is open. */
    private var window: Job? = null

    /**
     * Opens a grouping window if none is open.
     *
     * Must be called after enqueuing the marking, never before: this order
     * guarantees no marking is lost. An already-open window has not yet begun
     * transmitting (it closes first), so it will carry the marking just
     * enqueued; and if it closed in the meantime, this call opens a new one.
     */
    suspend fun schedule() {
        windowMutex.withLock {
            if (window != null) return
            window =
                scope.launch {
                    delay(groupingDelayMillis)
                    /*
                     * The window closes BEFORE transmitting, and that is the
                     * whole invariant: a marking arriving during transmission
                     * finds the slot free and opens its own window. Closing it
                     * after would leave that marking with no scheduled
                     * transmission, while the in-flight one may already have
                     * read the queue.
                     */
                    releaseWindow(coroutineContext.job)
                    transmitExclusively()
                }
        }
    }

    /**
     * Transmits without waiting for the window to end.
     *
     * Used by the startup replay, where there is nothing to group, only to
     * catch up, and by going to background, where waiting is pointless since
     * nothing more will be read.
     */
    suspend fun transmitNow() {
        closeWindow()
        transmitExclusively()
    }

    /**
     * Abandons the current window without transmitting.
     *
     * For sign-out: the queue being dropped, the scheduled transmission has
     * nothing left to tell the server.
     */
    suspend fun cancelScheduled() {
        closeWindow()
    }

    private suspend fun closeWindow() {
        windowMutex.withLock {
            window?.cancel()
            window = null
        }
    }

    /**
     * Frees the slot, for use by the window itself.
     *
     * Distinct from [closeWindow], which cancels: a window cannot cancel
     * itself without cancelling the transmission it is about to trigger. The
     * identity comparison guards the race where a forced dispatch already
     * closed this window and another was opened since; without it, someone
     * else's window would be lost.
     */
    private suspend fun releaseWindow(current: Job) {
        windowMutex.withLock { if (window === current) window = null }
    }

    private suspend fun transmitExclusively() = transmissionMutex.withLock { transmit() }
}
