package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.RelativeTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Tests for the displayed-order publication in isolation, mirror of
 * [PublishFeedRecapTest]: the "recap starts at the first visible article"
 * rule (author's ruling, 2026-08-18) lives entirely here.
 */
@RunWith(RobolectricTestRunner::class)
class PublishDisplayedArticlesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var firstDisplayedIndex by mutableIntStateOf(0)
    private var published: List<ArticleId> = emptyList()

    private fun show(articles: List<ArticleUiModel>) {
        composeRule.setContent {
            PublishDisplayedArticles(
                articles = articles,
                onDisplayedArticlesChange = { published = it },
                firstDisplayedIndex = { firstDisplayedIndex },
            )
        }
    }

    @Test
    fun theWholeOrderIsPublishedWhenTheListSitsAtTheTop() {
        show(articles((1L..3L)))

        assertEquals(ids(1L, 2L, 3L), published)
    }

    @Test
    fun articlesScrolledPastAreLeftOutOfTheOrder() {
        firstDisplayedIndex = 2
        show(articles((1L..4L)))

        assertEquals(ids(3L, 4L), published)
    }

    @Test
    fun scrollingRepublishesFromTheNewFirstVisibleArticle() {
        show(articles((1L..3L)))

        firstDisplayedIndex = 1
        composeRule.waitForIdle()

        assertEquals(ids(2L, 3L), published)
    }

    @Test
    fun anIndexBeyondTheArticlesPublishesAnEmptyOrder() {
        // The list's footer can be the first visible item while a refresh
        // empties the feed under the scroll position.
        firstDisplayedIndex = 5
        show(articles((1L..2L)))

        assertEquals(emptyList(), published)
    }

    private fun articles(idRange: LongRange): List<ArticleUiModel> = idRange.map { id ->
        ArticleUiModel(
            id = id,
            title = "Titre $id",
            feedTitle = "Le flux",
            publishedAt = RelativeTime.Hours(1),
            excerpt = "",
        )
    }

    private fun ids(vararg values: Long): List<ArticleId> = values.map(::ArticleId)
}
