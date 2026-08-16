package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

    private fun show(
        state: RecapSheetState,
        onDownloadConfirm: () -> Unit = {},
        onItemClick: (String) -> Unit = {},
        onLoadMore: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                RecapSheetContent(
                    state = state,
                    onDownloadConfirm = onDownloadConfirm,
                    onItemClick = onItemClick,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }

    private fun segment(
        text: String = "Le brief.",
        url: String? = "https://exemple.org/article",
    ) = RecapSegmentUi(text = text, url = url)

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

        composeRule.onNodeWithText("Aucun article à résumer.").assertIsDisplayed()
    }

    @Test
    fun beforeTheFirstWordsTheParagraphSkeletonShimmers() {
        // The shimmer sweeps forever: without freezing the clock, waiting
        // for idle would never end.
        composeRule.mainClock.autoAdvance = false
        show(RecapSheetState.Digest(segments = emptyList(), isGenerating = true, canLoadMore = false))

        composeRule.onAllNodesWithTag(RecapTestTags.SKELETON).assertCountEquals(4)
    }

    @Test
    fun theBriefReadsAsOneParagraph() {
        show(
            RecapSheetState.Digest(
                segments = listOf(
                    segment(text = "GNOME ouvre sa bêta"),
                    segment(text = ", et le procès continue.", url = null),
                ),
                isGenerating = false,
                canLoadMore = false,
            ),
        )

        composeRule.onNodeWithText("GNOME ouvre sa bêta, et le procès continue.").assertIsDisplayed()
    }

    @Test
    fun onceDoneWithUnreadLeftTheLoadMorePillCloses() {
        var more = 0
        show(
            RecapSheetState.Digest(
                segments = listOf(segment()),
                isGenerating = false,
                canLoadMore = true,
            ),
            onLoadMore = { more++ },
        )

        composeRule.onNodeWithTag(RecapTestTags.LOAD_MORE).performClick()

        assertEquals(1, more)
    }

    @Test
    fun whileGeneratingThePillStaysAway() {
        composeRule.mainClock.autoAdvance = false
        show(
            RecapSheetState.Digest(
                segments = listOf(segment()),
                isGenerating = true,
                canLoadMore = true,
            ),
        )

        composeRule.onAllNodesWithTag(RecapTestTags.LOAD_MORE).assertCountEquals(0)
    }

    @Test
    fun aGenerationFailureSaysSo() {
        show(RecapSheetState.GenerationFailed)

        composeRule.onNodeWithText("Le résumé n’a pas pu être généré. Réessayez plus tard.")
            .assertIsDisplayed()
    }
}
