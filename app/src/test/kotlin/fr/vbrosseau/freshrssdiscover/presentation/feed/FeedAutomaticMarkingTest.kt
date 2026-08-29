package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * Continuous display duration the detector actually applies.
 *
 * Derived, never copied: these cases probe the threshold from both sides, and
 * a literal repeated in three files would keep asserting an old value after a
 * change to the default — passing while describing behaviour the application
 * no longer has.
 */
private val VISIBILITY_THRESHOLD_MILLIS = ReadingSettings.Default.continuousVisibilityMillis

/**
 * What the automatic-marking switch stops, and what it does not
 * (SPECS.md §4.5, §4.7), in List mode.
 *
 * Kept separate from `FeedViewModelTest`: that file reached the size
 * Detekt refuses to exceed, and the setting forms its own subject with a
 * starting state shared by all its cases and used by no other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedAutomaticMarkingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val readSyncRepository = FakeReadSyncRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val freshnessRepository = FakeFeedFreshnessRepository()
    private val clock = FakeClock(NOW_MILLIS)

    /** Lazy: the ViewModel loads on creation, on `Dispatchers.Main`. */
    private val viewModel: FeedViewModel by lazy {
        FeedViewModel(
            articleRepository = repository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
    }

    private val state get() = viewModel.uiState.value

    private val readArticles: Set<ArticleId> get() = readSyncRepository.markCalls.flatten().toSet()

    /** Takes [id] past both thresholds, with two observations one second apart. */
    private fun watch(id: Long) {
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)
        viewModel.onVisibilityChanged(mapOf(ArticleId(id) to 1f))
    }

    @Test
    fun withAutomaticMarkingOnAVisibleArticleBecomesRead() {
        // Control case: without it, the following cases would pass just as
        // well if marking were broken for everyone.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        watch(id = 1L)

        assertEquals(setOf(ArticleId(1L)), readArticles)
    }

    @Test
    fun withAutomaticMarkingOffAVisibleArticleStaysUnread() {
        // The purpose of the setting: browsing the feed without consuming it.
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        watch(id = 1L)

        assertEquals(emptySet(), readArticles)
        assertFalse(state.articles.single().isRead)
    }

    @Test
    fun withAutomaticMarkingOffOpeningAnArticleStillMarksItRead() {
        // The switch only stops visibility-based detection. Opening an article
        // remains a deliberate gesture (SPECS.md §4.7) and marks it, otherwise
        // the feed would show it again indefinitely.
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)

        assertTrue(viewModel.onArticleOpened(1L))

        assertEquals(setOf(ArticleId(1L)), readArticles)
        assertTrue(state.articles.single().isRead)
    }

    @Test
    fun turningAutomaticMarkingBackOnResumesWithoutARestart() {
        // SPECS.md §6: the setting applies without a restart, through the
        // already observed settings flow, not through a second source to watch.
        settingsRepository.setAutomaticMarking(enabled = false)
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)
        watch(id = 1L)

        settingsRepository.setAutomaticMarking(enabled = true)
        watch(id = 1L)

        assertEquals(setOf(ArticleId(1L)), readArticles)
    }

    @Test
    fun turningAutomaticMarkingOffMidWatchDropsTheRunningCountdown() {
        // A timer started under the old setting does not survive the switch-off:
        // letting it complete would mark an article after the user asked for
        // this to stop.
        repository.enqueuePage(listOf(article(id = 1L)), nextCursor = null)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))
        clock.advanceBy(VISIBILITY_THRESHOLD_MILLIS)

        settingsRepository.setAutomaticMarking(enabled = false)
        viewModel.onVisibilityChanged(mapOf(ArticleId(1L) to 1f))

        assertEquals(emptySet(), readArticles)
    }
}
