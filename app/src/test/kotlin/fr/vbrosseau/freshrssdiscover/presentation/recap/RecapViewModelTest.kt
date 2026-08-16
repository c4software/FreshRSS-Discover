package fr.vbrosseau.freshrssdiscover.presentation.recap

import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.recap.FakeRecapGenerator
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapAvailability
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapDownloadEvent
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The recap sequencing: the button rule (unusable hides it, not-downloaded
 * still shows it), the download that flows into generation, and the failures
 * that each land on their own sheet state.
 */
class RecapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val generator = FakeRecapGenerator()
    private val articles = FakeArticleRepository()

    private fun viewModel(language: String = "French") =
        RecapViewModel(generator, articles, { language })

    @Test
    fun anUnsupportedDeviceKeepsTheButtonHidden() = runTest {
        generator.availability = RecapAvailability.Unavailable

        assertFalse(viewModel().uiState.value.isModelUsable)
    }

    @Test
    fun anAvailableModelShowsTheButton() = runTest {
        generator.availability = RecapAvailability.Available

        assertTrue(viewModel().uiState.value.isModelUsable)
    }

    @Test
    fun aDownloadableModelShowsTheButtonToo() = runTest {
        // The capability exists, only the weights are missing: hiding the
        // button here would keep the feature invisible forever, since the
        // download is offered behind it.
        generator.availability = RecapAvailability.Downloadable

        assertTrue(viewModel().uiState.value.isModelUsable)
    }

    @Test
    fun requestingWithAReadyModelStreamsTheDigest() = runTest {
        articles.unreadInCache = listOf(article(title = "Le seul titre", url = "https://exemple.org/a"))
        generator.chunks = listOf("1. Un début", ", une fin.")

        val viewModel = viewModel()
        viewModel.onRecapRequested()

        assertEquals(
            RecapSheetState.Digest(
                items = listOf(
                    RecapItemUi(
                        title = "Le seul titre",
                        summary = "Un début, une fin.",
                        url = "https://exemple.org/a",
                    ),
                ),
                plannedCount = 1,
                isGenerating = false,
            ),
            viewModel.uiState.value.sheet,
        )
    }

    @Test
    fun thePromptCarriesTheArticlesAndTheDeviceLanguage() = runTest {
        articles.unreadInCache = listOf(article(title = "Le seul titre"))

        viewModel(language = "Italian").onRecapRequested()

        val prompt = generator.receivedPrompts.single()
        assertContains(prompt, "Le seul titre")
        assertContains(prompt, "written in Italian")
    }

    @Test
    fun anEmptyCacheShowsTheEmptyStateWithoutTouchingTheModel() = runTest {
        val viewModel = viewModel()
        viewModel.onRecapRequested()

        assertEquals(RecapSheetState.Empty, viewModel.uiState.value.sheet)
        assertTrue(generator.receivedPrompts.isEmpty())
    }

    @Test
    fun aGenerationFailureLandsOnTheFailedState() = runTest {
        articles.unreadInCache = listOf(article())
        generator.chunks = listOf("Un début")
        generator.generationFailure = IllegalStateException("le modèle est mort")

        val viewModel = viewModel()
        viewModel.onRecapRequested()

        // What arrived is discarded: half a digest reads as a whole one.
        assertEquals(RecapSheetState.GenerationFailed, viewModel.uiState.value.sheet)
    }

    @Test
    fun aMissingModelOffersTheDownloadInsteadOfGenerating() = runTest {
        generator.availability = RecapAvailability.Downloadable

        val viewModel = viewModel()
        viewModel.onRecapRequested()

        assertEquals(RecapSheetState.DownloadOffer, viewModel.uiState.value.sheet)
        assertTrue(generator.receivedPrompts.isEmpty())
    }

    @Test
    fun aConfirmedDownloadFlowsStraightIntoGeneration() = runTest {
        generator.availability = RecapAvailability.Downloadable
        articles.unreadInCache = listOf(article())
        generator.chunks = listOf("Le récap.")

        val viewModel = viewModel()
        viewModel.onRecapRequested()
        viewModel.onDownloadConfirmed()

        // Prose without numbers degrades to one unlinked item, not a blank.
        assertEquals(
            RecapSheetState.Digest(
                items = listOf(RecapItemUi(title = null, summary = "Le récap.", url = null)),
                plannedCount = 1,
                isGenerating = false,
            ),
            viewModel.uiState.value.sheet,
        )
    }

    @Test
    fun aFailedDownloadOffersTheRetry() = runTest {
        generator.availability = RecapAvailability.Downloadable
        generator.downloadEvents = listOf(
            RecapDownloadEvent.Progress(totalBytesDownloaded = 1_024L),
            RecapDownloadEvent.Failed,
        )

        val viewModel = viewModel()
        viewModel.onRecapRequested()
        viewModel.onDownloadConfirmed()

        assertEquals(RecapSheetState.DownloadFailed, viewModel.uiState.value.sheet)
    }

    @Test
    fun dismissingTheSheetHidesIt() = runTest {
        articles.unreadInCache = listOf(article())
        generator.chunks = listOf("Le récap.")

        val viewModel = viewModel()
        viewModel.onRecapRequested()
        viewModel.onSheetDismissed()

        assertEquals(RecapSheetState.Hidden, viewModel.uiState.value.sheet)
    }

    @Test
    fun aSecondTapWhileTheSheetIsOpenRestartsNothing() = runTest {
        articles.unreadInCache = listOf(article())
        generator.chunks = listOf("Le récap.")

        val viewModel = viewModel()
        viewModel.onRecapRequested()
        viewModel.onRecapRequested()

        assertIs<RecapSheetState.Digest>(viewModel.uiState.value.sheet)
        assertEquals(1, generator.receivedPrompts.size)
    }
}
