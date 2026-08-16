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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    private fun show(showsProgress: Boolean) {
        composeRule.setContent {
            PublishFeedRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {},
                onFeedRefreshChange = { published = it },
                showsProgress = showsProgress,
            )
        }
    }

    @Test
    fun aRunningRefreshKeepsTheActionPublishedWithoutItsSpinner() {
        // List mode: the pull indicator animates for every refresh; the bar
        // button stays put, disabled — withdrawing it used to shift the
        // recap button beside it (GOAL-037-T14).
        show(showsProgress = false)

        isRefreshing = true
        composeRule.waitForIdle()

        val refresh = assertNotNull(published)
        assertTrue(refresh.isRefreshing)
        assertFalse(refresh.showsProgress)
    }

    @Test
    fun byDefaultTheActionStaysPublishedWhileRefreshing() {
        // Swipe mode has no other indicator: the button itself shows progress.
        show(showsProgress = true)

        isRefreshing = true
        composeRule.waitForIdle()

        val refresh = assertNotNull(published)
        assertTrue(refresh.isRefreshing)
        assertTrue(refresh.showsProgress)
    }
}
