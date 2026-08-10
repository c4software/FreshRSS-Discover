package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedRefresh
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the refresh-action publication in isolation: routes need injected
 * ViewModels, while the withdrawal rule lives entirely in [PublishFeedRefresh].
 */
@RunWith(RobolectricTestRunner::class)
class PublishFeedRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var isRefreshing by mutableStateOf(false)
    private var published: FeedRefresh? = null

    private fun show(hideWhileRefreshing: Boolean) {
        composeRule.setContent {
            PublishFeedRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {},
                onFeedRefreshChange = { published = it },
                hideWhileRefreshing = hideWhileRefreshing,
            )
        }
    }

    @Test
    fun aRunningRefreshWithdrawsTheActionWhenTheScreenShowsItsOwnIndicator() {
        // List mode: the pull indicator animates for every refresh; keeping
        // the bar button's spinner too would run two animations at once.
        show(hideWhileRefreshing = true)

        isRefreshing = true
        composeRule.waitForIdle()

        assertNull(published)
    }

    @Test
    fun theActionComesBackOnceTheRefreshEnds() {
        show(hideWhileRefreshing = true)
        isRefreshing = true
        composeRule.waitForIdle()

        isRefreshing = false
        composeRule.waitForIdle()

        assertNotNull(published)
    }

    @Test
    fun byDefaultTheActionStaysPublishedWhileRefreshing() {
        // Swipe mode has no other indicator: the button itself shows progress.
        show(hideWhileRefreshing = false)

        isRefreshing = true
        composeRule.waitForIdle()

        val refresh = assertNotNull(published)
        assertTrue(refresh.isRefreshing)
    }
}
