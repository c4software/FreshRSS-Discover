package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.test.assertIsDisplayed
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
 * Tests for the recap button in isolation, like the refresh button beside
 * it: it lives in the top bar, not in the screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class RecapButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(onRecap: () -> Unit = {}) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                RecapButton(onRecap = onRecap)
            }
        }
    }

    @Test
    fun pressingItAsksForARecap() {
        var recaps = 0
        show(onRecap = { recaps++ })

        composeRule.onNodeWithTag(RecapTestTags.BUTTON).performClick()

        assertEquals(1, recaps)
    }

    @Test
    fun itAnnouncesWhatItDoesToAScreenReader() {
        // SPECS.md §7.1: an icon alone means nothing without a description.
        show()

        composeRule.onNodeWithContentDescription("Résumer les articles non lus").assertIsDisplayed()
    }
}
