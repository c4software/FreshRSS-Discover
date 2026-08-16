package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Tests for the animated title-bar slot. The animation itself is not
 * asserted; what matters is that the button ends up present or absent.
 */
@RunWith(RobolectricTestRunner::class)
class FeedRefreshActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var refresh by mutableStateOf<FeedRefresh?>(null)

    private fun show() {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                FeedRefreshAction(refresh = refresh)
            }
        }
    }

    @Test
    fun aPublishedActionShowsTheButton() {
        refresh = FeedRefresh(isRefreshing = false, showsProgress = true, onRefresh = {})
        show()

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).assertIsDisplayed()
    }

    @Test
    fun aWithdrawnActionRemovesTheButton() {
        refresh = FeedRefresh(isRefreshing = false, showsProgress = true, onRefresh = {})
        show()

        refresh = null
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).assertDoesNotExist()
    }

    @Test
    fun theButtonStaysWiredToThePublishedAction() {
        var reloads = 0
        refresh = FeedRefresh(isRefreshing = false, showsProgress = true, onRefresh = { reloads++ })
        show()

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).performClick()

        assertEquals(1, reloads)
    }
}
