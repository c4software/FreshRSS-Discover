package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.swipe.SwipeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * A refresh reports what was read to the server before querying it (GOAL-024).
 *
 * The defect these cases lock down: read marks are batched for five seconds
 * before being transmitted (SPECS.md §8, question 4). A refresh requested
 * inside that window queried a server that still considered the just-read
 * articles unread; it returned them, they took their place in the page of
 * forty, and new articles did not appear. Two refreshes were needed.
 *
 * Why a hook rather than two counters: `flushCallCount == 1` and
 * `refreshCallCount == 1` would both hold in the faulty order; two counters
 * say that two things happened, never which came first.
 * `FakeArticleRepository.onRefresh` observes the state at the moment the
 * refresh starts, the only way to test an ordering.
 *
 * Both modes get a case: they carry the same rule in two distinct ViewModels,
 * and a fix applied on one side only would make them diverge
 * (ARCHITECTURE.md §9.6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshFlushesPendingMarksTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_MILLIS)

    /**
     * Flush count observed at the moment the refresh starts.
     *
     * `-1` until the refresh has happened: a value that cannot be confused
     * with "no flush", which is zero and would be exactly the defect.
     */
    private var flushesWhenRefreshStarted = -1

    private fun recordFlushCountAtRefresh() {
        repository.onRefresh = { flushesWhenRefreshStarted = readSyncRepository.flushCallCount }
    }

    /**
     * The ViewModel already calls `flush()` in its constructor, the startup
     * replay of SPECS.md §4.5. That count must be subtracted, or the case
     * would pass on a flush unrelated to the gesture.
     */
    private fun flushesCausedByRefresh(atStartup: Int): Int = flushesWhenRefreshStarted - atStartup

    @Test
    fun listModeTransmitsWhatWasReadBeforeAskingTheServerAgain() {
        val viewModel = DiscoverViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
        val atStartup = readSyncRepository.flushCallCount
        recordFlushCountAtRefresh()

        viewModel.refresh()

        assertEquals(
            1,
            flushesCausedByRefresh(atStartup),
            "le rechargement doit transmettre les marquages en attente avant d'interroger le serveur",
        )
    }

    @Test
    fun swipeModeTransmitsWhatWasReadBeforeAskingTheServerAgain() {
        val viewModel = SwipeViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
        val atStartup = readSyncRepository.flushCallCount
        recordFlushCountAtRefresh()

        viewModel.refresh()

        assertEquals(
            1,
            flushesCausedByRefresh(atStartup),
            "le mode Balayage porte la même règle : un correctif d'un seul côté ferait diverger les deux modes",
        )
    }
}
