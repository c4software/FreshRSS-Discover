package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.vbrosseau.freshrssdiscover.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Tests for the sheet content, state by state. The content is tested rather
 * than `RecapSheet`: the modal renders in its own window, and what varies is
 * entirely in the content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR")
class RecapSheetContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: RecapSheetState, onDownloadConfirm: () -> Unit = {}) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                RecapSheetContent(state = state, onDownloadConfirm = onDownloadConfirm)
            }
        }
    }

    @Test
    fun theOfferExplainsAndItsButtonStartsTheDownload() {
        var downloads = 0
        show(RecapSheetState.DownloadOffer, onDownloadConfirm = { downloads++ })

        composeRule.onNodeWithText(
            "Le résumé est produit sur votre appareil, et rien n’en sort. " +
                "Cela demande de télécharger le modèle, une seule fois.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(RecapTestTags.DOWNLOAD).performClick()

        assertEquals(1, downloads)
    }

    @Test
    fun theDownloadProgressSpeaksInMegabytes() {
        show(RecapSheetState.Downloading(totalBytesDownloaded = 3_145_728L))

        composeRule.onNodeWithText("Téléchargement du modèle… 3 Mo").assertIsDisplayed()
    }

    @Test
    fun aFailedDownloadOffersTheSameButtonAgain() {
        var downloads = 0
        show(RecapSheetState.DownloadFailed, onDownloadConfirm = { downloads++ })

        composeRule.onNodeWithTag(RecapTestTags.DOWNLOAD).performClick()

        assertEquals(1, downloads)
    }

    @Test
    fun anEmptyFeedSaysSo() {
        show(RecapSheetState.Empty)

        composeRule.onNodeWithText("Aucun article non lu à résumer.").assertIsDisplayed()
    }

    @Test
    fun beforeTheFirstWordsTheSheetSaysItIsSummarizing() {
        // The spark pulses forever: without freezing the clock, waiting for
        // idle would never end.
        composeRule.mainClock.autoAdvance = false
        show(RecapSheetState.Digest(text = "", isGenerating = true))

        composeRule.onNodeWithText("Résumé en cours…").assertIsDisplayed()
    }

    @Test
    fun theStreamingTextEndsOnTheInsertionMark() {
        composeRule.mainClock.autoAdvance = false
        show(RecapSheetState.Digest(text = "Un début", isGenerating = true))

        composeRule.onNodeWithText("Un début▍").assertIsDisplayed()
    }

    @Test
    fun theModelMarkdownIsRenderedInsteadOfShownRaw() {
        show(RecapSheetState.Digest(text = "* **Thème :** le texte", isGenerating = false))

        composeRule.onNodeWithText("• Thème : le texte").assertIsDisplayed()
    }

    @Test
    fun theDigestTextIsDisplayed() {
        show(RecapSheetState.Digest(text = "• Le récap du jour.", isGenerating = false))

        composeRule.onNodeWithTag(RecapTestTags.DIGEST).assertIsDisplayed()
        composeRule.onNodeWithText("• Le récap du jour.").assertIsDisplayed()
    }

    @Test
    fun aGenerationFailureSaysSo() {
        show(RecapSheetState.GenerationFailed)

        composeRule.onNodeWithText("Le résumé n’a pas pu être généré. Réessayez plus tard.")
            .assertIsDisplayed()
    }
}
