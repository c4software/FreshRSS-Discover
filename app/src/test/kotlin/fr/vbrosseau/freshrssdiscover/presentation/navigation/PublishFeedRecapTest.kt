package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import fr.vbrosseau.freshrssdiscover.presentation.recap.FeedRecap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the recap-action publication in isolation, mirror of
 * [PublishFeedRefreshTest]: the dynamic-button rule lives entirely here.
 */
@RunWith(RobolectricTestRunner::class)
class PublishFeedRecapTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var isModelUsable by mutableStateOf(false)
    private var published: FeedRecap? = null

    private fun show(onRecap: () -> Unit = {}) {
        composeRule.setContent {
            PublishFeedRecap(
                isModelUsable = isModelUsable,
                onRecap = onRecap,
                onFeedRecapChange = { published = it },
            )
        }
    }

    @Test
    fun nothingIsPublishedWhileTheModelIsNotUsable() {
        // Also the initial state: the button must not flash on a device
        // that turns out unsupported once the platform answers.
        show()

        assertNull(published)
    }

    @Test
    fun theActionAppearsOnceThePlatformSaysTheModelIsUsable() {
        show()

        isModelUsable = true
        composeRule.waitForIdle()

        assertNotNull(published)
    }

    @Test
    fun thePublishedActionStaysWiredToTheCallback() {
        var recaps = 0
        show(onRecap = { recaps++ })
        isModelUsable = true
        composeRule.waitForIdle()

        assertNotNull(published).onRecap()

        assertEquals(1, recaps)
    }
}
