package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The immersive feed's claim on the whole screen, in isolation: the route
 * needs injected ViewModels, while the rule lives in [PublishFeedFillsScreen].
 */
@RunWith(RobolectricTestRunner::class)
class PublishFeedFillsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var shown by mutableStateOf(true)
    private val published = mutableListOf<Boolean>()

    @Test
    fun theClaimIsPublishedWhileShownAndWithdrawnWhenLeft() {
        // Withdrawal is the case that matters: a bar left transparent over
        // the settings screen would lose its title on a white background.
        composeRule.setContent {
            if (shown) PublishFeedFillsScreen(onFeedFillsScreenChange = published::add)
        }
        composeRule.waitForIdle()
        assertEquals(listOf(true), published)

        shown = false
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), published)
    }
}
