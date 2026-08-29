package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * The unreachable-server toast event (GOAL-030): emitted by the engine on
 * load and reload failures, and only for `ServerUnreachable` — being offline
 * already has the banner as its regime (SPECS.md §5.2). Tested on List mode;
 * the engine being shared, Immersive follows.
 *
 * The collector always subscribes after the failing call: that also proves
 * the buffering — an event landing while nobody collects, a configuration
 * change being the real case, is delivered to the next collector instead of
 * being dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedServerUnreachableEventTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val repository = FakeArticleRepository()

    private val collectorScope = CoroutineScope(dispatcher)

    /** Lazy: the ViewModel loads on creation, on `Dispatchers.Main`. */
    private val viewModel: FeedViewModel by lazy {
        FeedViewModel(
            articleRepository = repository,
            readSyncRepository = FakeReadSyncRepository(),
            settingsRepository = FakeSettingsRepository(),
            freshnessRepository = FakeFeedFreshnessRepository(),
            clock = FakeClock(NOW_MILLIS),
        )
    }

    @After
    fun stopCollecting() {
        collectorScope.cancel()
    }

    /** Collects on the unconfined dispatcher: events land in the list as they are sent. */
    private fun collectedEvents(): MutableList<FeedEvent> {
        val events = mutableListOf<FeedEvent>()
        collectorScope.launch { viewModel.events.collect(events::add) }
        return events
    }

    @Test
    fun anUnreachableServerOnLoadEmitsTheToastEvent() {
        repository.enqueueFailure(FeedError.ServerUnreachable)

        assertEquals(listOf(FeedEvent.ServerUnreachable), collectedEvents())
    }

    @Test
    fun anUnreachableServerOnReloadEmitsTheToastEvent() {
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = PageCursor("c1"))
        val events = collectedEvents()
        repository.enqueueFailure(FeedError.ServerUnreachable)

        viewModel.refresh()

        assertEquals(listOf(FeedEvent.ServerUnreachable), events)
    }

    @Test
    fun aServerAnsweringAnErrorEmitsTheToastEvent() {
        // "No OK answer" covers the server that answers wrongly too: an error
        // status or an unreadable body must be as noticeable as no answer.
        repository.enqueueFailure(FeedError.Unexpected("HTTP 503"))

        assertEquals(listOf(FeedEvent.ServerFailed), collectedEvents())
    }

    @Test
    fun beingOfflineEmitsNoToastEvent() {
        // The offline banner already owns that regime: a toast on top would
        // nag about a state the screen is already stating (SPECS.md §5.2).
        repository.enqueueFailure(FeedError.NoNetwork)

        assertTrue(collectedEvents().isEmpty())
    }

    @Test
    fun anExpiredSessionEmitsNoToastEvent() {
        // The root switch is already steering back to sign-in (SPECS.md §3.4):
        // toasting over a disappearing screen would explain nothing.
        repository.enqueueFailure(FeedError.SessionExpired)

        assertTrue(collectedEvents().isEmpty())
    }
}
