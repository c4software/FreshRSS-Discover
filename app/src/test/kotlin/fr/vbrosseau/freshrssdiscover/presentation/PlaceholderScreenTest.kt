package fr.vbrosseau.freshrssdiscover.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Vérifie l'ossature d'affichage minimale.
 *
 * Le contenu de cet écran est provisoire, mais le chemin qu'il emprunte —
 * thème, ressources de chaînes, `testTag` — est celui qu'emprunteront les
 * écrans réels. Ce test échoue donc si ce chemin casse, bien avant qu'un écran
 * ne l'utilise.
 */
@RunWith(RobolectricTestRunner::class)
class PlaceholderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theScreenAnnouncesThatItsContentIsStillToCome() {
        composeRule.setContent { PlaceholderScreen() }

        composeRule.onNodeWithTag(PlaceholderTestTags.ROOT).assertExists()
        composeRule.onNodeWithText("Écran à venir").assertExists()
    }
}
