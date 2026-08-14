package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the tab-reselection publication in isolation, like
 * [PublishFeedRefreshTest]: routes need injected ViewModels, while both the
 * withdrawal rule and the scroll-then-reload sequence live here.
 */
@RunWith(RobolectricTestRunner::class)
class PublishFeedReselectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var published: (() -> Unit)? = null

    @Test
    fun theCallbackIsWithdrawnWhenTheDestinationLeaves() {
        // Without the withdrawal, leaving the feed for settings would keep
        // the tab wired to a ViewModel no longer on screen.
        var shown by mutableStateOf(true)
        composeRule.setContent {
            if (shown) {
                PublishFeedReselect(
                    onFeedReselectChange = { published = it },
                    onReselect = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertNotNull(published)

        shown = false
        composeRule.waitForIdle()

        assertNull(published)
    }

    @Test
    fun aReselectionReturnsToTheTopBeforeReloading() {
        // Sentinel: stays negative if the reload never fires, and records the
        // scroll position at the moment it does.
        var indexAtRefresh = -1

        composeRule.setContent {
            val listState = rememberLazyListState()

            PublishFeedReselect(
                onFeedReselectChange = { published = it },
                onReselect = rememberScrollToTopThenRefresh(
                    listState = listState,
                    onRefresh = { indexAtRefresh = listState.firstVisibleItemIndex },
                ),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .height(ListHeight)
                    .testTag(LIST_TAG),
            ) {
                items(count = 100) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ItemHeight),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LIST_TAG).performScrollToIndex(50)
        composeRule.waitForIdle()

        val reselect = assertNotNull(published)
        composeRule.runOnIdle { reselect() }
        composeRule.waitForIdle()

        // Zero, not merely "called": the reload snaps the list to the first
        // article, so firing it away from the top would make the animated
        // return a decoration that never shows.
        assertEquals(0, indexAtRefresh)
    }
}

private val ListHeight = 200.dp
private val ItemHeight = 48.dp

/** Tag of the test-only list standing in for the Discover feed. */
private const val LIST_TAG = "reselect:list"
