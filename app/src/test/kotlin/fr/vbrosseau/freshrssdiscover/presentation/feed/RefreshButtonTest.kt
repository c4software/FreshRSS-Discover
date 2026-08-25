package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Tests for the refresh button in isolation.
 *
 * The button lives in the top bar, not in the screens: testing it through
 * `DiscoverScreen` or `ImmersiveScreen` would tie it to a screen that no longer
 * owns it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class RefreshButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        isRefreshing: Boolean = false,
        showsProgress: Boolean = true,
        onRefresh: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                RefreshButton(
                    isRefreshing = isRefreshing,
                    showsProgress = showsProgress,
                    onRefresh = onRefresh,
                )
            }
        }
    }

    @Test
    fun pressingItAsksForAReload() {
        var reloads = 0
        show(onRefresh = { reloads++ })

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).performClick()

        assertEquals(1, reloads)
    }

    @Test
    fun aSecondPressIsIgnoredWhileTheReloadIsRunning() {
        var reloads = 0
        show(isRefreshing = true, onRefresh = { reloads++ })

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).performClick()

        assertEquals(0, reloads)
    }

    @Test
    fun itStaysInPlaceWhileTheReloadRunsRatherThanDisappearing() {
        // Hiding it would leave a gap and make the press feel lost; graying it
        // out would read as "unavailable" rather than "in progress".
        show(isRefreshing = true)

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).assertIsDisplayed()
    }

    @Test
    fun withoutItsOwnProgressItGreysOutInsteadOfSpinning() {
        // List mode: the pull indicator already animates, and the button
        // vanishing next to the recap one read as a glitch (GOAL-037-T14).
        var reloads = 0
        show(isRefreshing = true, showsProgress = false, onRefresh = { reloads++ })

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).assertIsDisplayed().assertIsNotEnabled()
        assertEquals(0, reloads)
    }

    @Test
    fun itAnnouncesWhatItDoesToAScreenReader() {
        // SPECS.md §7.1: an icon alone means nothing without a description.
        show()

        composeRule.onNodeWithContentDescription("Recharger le flux").assertIsDisplayed()
    }
}
