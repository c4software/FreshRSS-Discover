package fr.vbrosseau.freshrssdiscover.presentation.navigation

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The bar is derived from [AppDestination]: these tests verify the derivation,
 * which covers any destination added later.
 */
@RunWith(RobolectricTestRunner::class)
class AppNavigationBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eachDestinationOfTheEnumHasItsOwnItem() {
        composeRule.setContent {
            AppNavigationBar(currentRoute = AppRoutes.DISCOVER, onSelect = {})
        }

        AppDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(NavigationTestTags.item(destination)).assertExists()
        }
    }

    @Test
    fun theItemMatchingTheCurrentRouteIsSelected() {
        composeRule.setContent {
            AppNavigationBar(currentRoute = AppRoutes.SETTINGS, onSelect = {})
        }

        composeRule.onNodeWithTag(NavigationTestTags.item(AppDestination.SETTINGS)).assertIsSelected()
    }

    @Test
    fun clickingAnItemReportsItsDestination() {
        var selected: AppDestination? = null
        composeRule.setContent {
            AppNavigationBar(currentRoute = AppRoutes.DISCOVER, onSelect = { selected = it })
        }

        composeRule.onNodeWithTag(NavigationTestTags.item(AppDestination.SETTINGS)).performClick()

        assertEquals(AppDestination.SETTINGS, selected)
    }

    @Test
    fun clickingTheAlreadySelectedItemReportsAReselectionNotAMove() {
        var selected: AppDestination? = null
        var reselected: AppDestination? = null
        composeRule.setContent {
            AppNavigationBar(
                currentRoute = AppRoutes.DISCOVER,
                onSelect = { selected = it },
                onReselect = { reselected = it },
            )
        }

        composeRule.onNodeWithTag(NavigationTestTags.item(AppDestination.DISCOVER)).performClick()

        assertEquals(AppDestination.DISCOVER, reselected)
        assertEquals(null, selected)
    }

    @Test
    fun anUnknownRouteSelectsNothing() {
        // Real case: a destination reached outside the bar, or the transient
        // state before the graph has published its first route.
        assertEquals(null, AppDestination.forRoute("route-inexistante"))
        assertEquals(null, AppDestination.forRoute(null))
    }
}
