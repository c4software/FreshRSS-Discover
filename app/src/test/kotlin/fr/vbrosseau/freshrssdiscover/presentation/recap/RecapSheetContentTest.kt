package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
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

    private fun item(
        title: String? = "Le titre",
        summary: String = "Le résumé.",
        url: String? = "https://exemple.org/article",
    ) = RecapItemUi(title = title, summary = summary, url = url)

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
    fun beforeTheFirstSummaryTheSkeletonShimmers() {
        // The shimmer sweeps forever: without freezing the clock, waiting
        // for idle would never end.
        composeRule.mainClock.autoAdvance = false
        show(RecapSheetState.Digest(items = emptyList(), plannedCount = 5, isGenerating = true, canLoadMore = false))

        composeRule.onAllNodesWithTag(RecapTestTags.SKELETON).assertCountEquals(5)
    }

    @Test
    fun eachSummaryShowsItsArticleTitleAndText() {
        show(
            RecapSheetState.Digest(
                items = listOf(item(title = "GNOME 51", summary = "La bêta est ouverte.")),
                plannedCount = 1,
                isGenerating = false,
                canLoadMore = false,
            ),
        )

        composeRule.onNodeWithText("GNOME 51").assertIsDisplayed()
        composeRule.onNodeWithText("La bêta est ouverte.").assertIsDisplayed()
    }

    @Test
    fun tappingASummaryOpensItsArticle() {
        var opened: String? = null
        show(
            RecapSheetState.Digest(
                items = listOf(item(url = "https://exemple.org/a")),
                plannedCount = 1,
                isGenerating = false,
                canLoadMore = false,
            ),
            onItemClick = { opened = it },
        )

        composeRule.onNodeWithTag(RecapTestTags.ITEM).performClick()

        assertEquals("https://exemple.org/a", opened)
    }

    @Test
    fun anUnlinkedSummaryIsNotTappable() {
        show(
            RecapSheetState.Digest(
                items = listOf(item(title = null, url = null)),
                plannedCount = 1,
                isGenerating = false,
                canLoadMore = false,
            ),
        )

        composeRule.onAllNodesWithTag(RecapTestTags.ITEM).onFirst().assertHasNoClickAction()
    }

    @Test
    fun theModelMarkdownIsRenderedInsteadOfShownRaw() {
        show(
            RecapSheetState.Digest(
                items = listOf(item(summary = "* **Thème :** le texte")),
                plannedCount = 1,
                isGenerating = false,
                canLoadMore = false,
            ),
        )

        composeRule.onNodeWithText("• Thème : le texte").assertIsDisplayed()
    }

    @Test
    fun onceDoneWithUnreadLeftTheLoadMorePillCloses() {
        var more = 0
        show(
            RecapSheetState.Digest(
                items = listOf(item()),
                plannedCount = 1,
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
                items = listOf(item()),
                plannedCount = 2,
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
