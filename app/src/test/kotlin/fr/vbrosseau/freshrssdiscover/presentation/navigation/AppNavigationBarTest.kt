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
 * La barre est dérivée de [AppDestination] : ces tests constatent que la
 * dérivation fonctionne, ce qui vaut pour toute destination ajoutée ensuite.
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
    fun anUnknownRouteSelectsNothing() {
        // Cas réel : une destination atteinte hors de la barre, ou l'état
        // transitoire avant que le graphe n'ait publié sa première route.
        assertEquals(null, AppDestination.forRoute("route-inexistante"))
        assertEquals(null, AppDestination.forRoute(null))
    }
}
