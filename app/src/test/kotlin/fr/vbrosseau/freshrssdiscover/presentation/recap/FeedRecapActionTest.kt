package fr.vbrosseau.freshrssdiscover.presentation.recap

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
 * Tests for the animated title-bar slot, mirror of `FeedRefreshActionTest`:
 * the animation itself is not asserted, only presence and wiring.
 */
@RunWith(RobolectricTestRunner::class)
class FeedRecapActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var recap by mutableStateOf<FeedRecap?>(null)

    private fun show() {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                FeedRecapAction(recap = recap)
            }
        }
    }

    @Test
    fun aPublishedActionShowsTheButton() {
        recap = FeedRecap(onRecap = {})
        show()

        composeRule.onNodeWithTag(RecapTestTags.BUTTON).assertIsDisplayed()
    }

    @Test
    fun aWithdrawnActionRemovesTheButton() {
        recap = FeedRecap(onRecap = {})
        show()

        recap = null
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RecapTestTags.BUTTON).assertDoesNotExist()
    }

    @Test
    fun theButtonStaysWiredToThePublishedAction() {
        var recaps = 0
        recap = FeedRecap(onRecap = { recaps++ })
        show()

        composeRule.onNodeWithTag(RecapTestTags.BUTTON).performClick()

        assertEquals(1, recaps)
    }
}
