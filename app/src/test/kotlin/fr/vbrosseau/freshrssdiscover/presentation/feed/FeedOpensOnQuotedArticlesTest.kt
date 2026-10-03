package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.FakeSettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.FakeClock
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import fr.vbrosseau.freshrssdiscover.reminder.ReminderOpening
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val NOW_MILLIS = 1_700_000_000_000L

/**
 * Touching the reminder opens the feed on the articles it quoted
 * (SPECS.md §4.9, GOAL-044).
 *
 * Kept apart from [FeedViewModelTest], which is at the size limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedOpensOnQuotedArticlesTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val repository = FakeArticleRepository()
    private val reminderOpening = ReminderOpening()

    private fun feedViewModel() = FeedViewModel(
        articleRepository = repository,
        readSyncRepository = FakeReadSyncRepository(),
        settingsRepository = FakeSettingsRepository(),
        freshnessRepository = FakeFeedFreshnessRepository(),
        clock = FakeClock(NOW_MILLIS),
        reminderOpening = reminderOpening,
    )

    private fun FeedViewModel.shownIds(): List<Long> = uiState.value.articles.map { it.id }

    private fun quote(vararg ids: Long) = reminderOpening.openedOn(ids.map(::ArticleId))

    @Test
    fun theQuotedArticlesComeFirstInTheOrderTheReminderQuotedThem() {
        repository.cachedArticles.value = (1L..5L).map { article(id = it) }
        quote(4L, 2L)

        assertEquals(listOf(4L, 2L, 1L, 3L, 5L), feedViewModel().shownIds())
    }

    @Test
    fun anOpeningWithoutReminderKeepsTheOrderOfTheCache() {
        repository.cachedArticles.value = (1L..5L).map { article(id = it) }

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), feedViewModel().shownIds())
    }

    @Test
    fun aQuotedArticleBeyondTheCacheSampleIsStillShownFirst() {
        // The feed samples a bounded number of cached articles, read ones
        // included: a quoted unread article can sit past that bound.
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))
        repository.unreadInCache = listOf(article(id = 9L))
        quote(9L)

        assertEquals(listOf(9L, 1L, 2L), feedViewModel().shownIds())
    }

    @Test
    fun aQuotedArticleGoneFromTheCacheIsSkipped() {
        repository.cachedArticles.value = listOf(article(id = 1L), article(id = 2L))
        quote(7L, 2L)

        assertEquals(listOf(2L, 1L), feedViewModel().shownIds())
    }

    @Test
    fun aLaterCacheWriteDoesNotMoveWhatIsShown() {
        repository.cachedArticles.value = (1L..3L).map { article(id = it) }
        quote(3L)
        val viewModel = feedViewModel()

        repository.cachedArticles.value = (1L..4L).map { article(id = it) }

        assertEquals(listOf(3L, 1L, 2L, 4L), viewModel.shownIds())
    }

    @Test
    fun aFeedBuiltAgainUnderTheSameActivityDoesNotReopenOnTheReminder() {
        // Sign-out then sign-in rebuilds the feed while the activity, and
        // the reminder it was opened from, stay the same.
        repository.cachedArticles.value = (1L..3L).map { article(id = it) }
        quote(3L)
        feedViewModel()

        assertEquals(listOf(1L, 2L, 3L), feedViewModel().shownIds())
    }
}
