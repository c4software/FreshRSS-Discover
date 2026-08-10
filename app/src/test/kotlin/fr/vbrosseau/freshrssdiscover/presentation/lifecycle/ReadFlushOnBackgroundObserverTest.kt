package fr.vbrosseau.freshrssdiscover.presentation.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The lifecycle is driven by a real [LifecycleRegistry] rather than by calling
 * `onStop` directly: what is verified is the mapping between event and flush,
 * and calling the method by hand would assume it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadFlushOnBackgroundObserverTest {

    /**
     * `UnconfinedTestDispatcher`: the flush starts from a `launch` triggered
     * outside any coroutine, with nothing to await it. Run in place, it is
     * observable as soon as the event returns.
     */
    private val applicationScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private val repository = FakeReadSyncRepository()

    @Test
    fun goingToBackgroundTransmitsPendingReads() {
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.CREATED

        assertEquals(1, repository.flushCallCount)
    }

    /**
     * The case that justifies `ON_STOP`: a system dialog comes to the front,
     * the app stays visible and reading resumes within a second. Flushing here
     * would defeat the batching instead of closing it.
     */
    @Test
    fun losingFocusWithoutLeavingDoesNotTransmit() {
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.STARTED

        assertEquals(0, repository.flushCallCount)
    }

    /**
     * With nothing queued, the forced flush sends nothing: the guard lives in
     * `flush`, which does not touch the network when the queue is empty. The
     * observer therefore does not need to inspect the queue before triggering.
     */
    @Test
    fun goingToBackgroundWithNothingPendingSendsNothing() {
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.CREATED

        assertEquals(0, repository.markedIds.size)
    }

    /**
     * Returning then leaving again must flush again: an observer firing only
     * once would only cover the first session.
     */
    @Test
    fun returningThenLeavingAgainTransmitsOnceMore() {
        val owner = ownerObservedFromResumed()

        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.RESUMED
        owner.registry.currentState = Lifecycle.State.CREATED

        assertEquals(2, repository.flushCallCount)
    }

    /**
     * Returns the owner, not just its registry: the registry only holds it
     * weakly and refuses to work once it has been garbage-collected.
     */
    private fun ownerObservedFromResumed(): TestLifecycleOwner = TestLifecycleOwner().apply {
        registry.addObserver(ReadFlushOnBackgroundObserver(repository, applicationScope))
        registry.currentState = Lifecycle.State.RESUMED
    }
}

/**
 * `createUnsafe`: the registry normally enforces main-thread access, which
 * does not exist in a pure JVM test. Nothing here is concurrent, so the check
 * protects nothing.
 */
private class TestLifecycleOwner : LifecycleOwner {
    val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}
