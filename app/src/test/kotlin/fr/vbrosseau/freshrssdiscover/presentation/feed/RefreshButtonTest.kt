package fr.vbrosseau.freshrssdiscover.presentation.feed

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
 * Le bouton de rechargement, éprouvé seul.
 *
 * Il a quitté les deux écrans pour la barre de titre : le tester à travers
 * `DiscoverScreen` ou `SwipeScreen` ne dirait donc plus rien de l'endroit où il
 * se trouve, et ferait dépendre son sort d'un écran qui ne le possède plus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class RefreshButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(isRefreshing: Boolean = false, onRefresh: () -> Unit = {}) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                RefreshButton(isRefreshing = isRefreshing, onRefresh = onRefresh)
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
        // Disparu, il laisserait un trou et l'appui semblerait perdu ; grisé,
        // il dirait « indisponible » et non « en cours ».
        show(isRefreshing = true)

        composeRule.onNodeWithTag(RefreshTestTags.BUTTON).assertIsDisplayed()
    }

    @Test
    fun itAnnouncesWhatItDoesToAScreenReader() {
        // SPECS.md §7.1 : une icône seule ne dit rien sans description.
        show()

        composeRule.onNodeWithContentDescription("Recharger le flux").assertIsDisplayed()
    }
}
