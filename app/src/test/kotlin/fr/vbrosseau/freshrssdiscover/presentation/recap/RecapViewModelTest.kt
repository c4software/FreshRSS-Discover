package fr.vbrosseau.freshrssdiscover.presentation.recap

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.article
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
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
    private val readSync = FakeReadSyncRepository()

    private fun viewModel(language: String = "French") =
        RecapViewModel(generator, articles, readSync, { language })

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
                canLoadMore = false,
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
                canLoadMore = false,
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
    fun loadMoreReplacesTheCardsWithTheNextBatch() = runTest {
        articles.unreadInCache = (1L..7L).map { article(id = it, title = "Titre $it") }
        generator.chunks = listOf("1. R1\n2. R2\n3. R3\n4. R4\n5. R5")

        val viewModel = viewModel()
        viewModel.onRecapRequested()
        generator.chunks = listOf("1. R6\n2. R7")
        viewModel.onLoadMore()

        val sheet = viewModel.uiState.value.sheet
        assertIs<RecapSheetState.Digest>(sheet)
        // A page of five, not a pile: only the next batch remains on show,
        // and no already-summarized article comes back.
        assertEquals(listOf("Titre 6", "Titre 7"), sheet.items.map { it.title })
        assertEquals(2, sheet.plannedCount)
        assertFalse(sheet.canLoadMore)
        assertFalse(sheet.isGenerating)
    }

    @Test
    fun moreUnreadThanTheCapOffersToLoadMore() = runTest {
        articles.unreadInCache = (1L..6L).map { article(id = it) }
        generator.chunks = listOf("1. R1")

        val viewModel = viewModel()
        viewModel.onRecapRequested()

        val sheet = viewModel.uiState.value.sheet
        assertIs<RecapSheetState.Digest>(sheet)
        assertTrue(sheet.canLoadMore)
    }

    @Test
    fun theSummariesFollowTheOrderDisplayedOnScreen() = runTest {
        articles.unreadInCache = (1L..3L).map { article(id = it, title = "Titre $it") }
        generator.chunks = listOf("1. R1\n2. R2\n3. R3")

        val viewModel = viewModel()
        viewModel.onDisplayedOrderChanged(listOf(ArticleId(3L), ArticleId(1L)))
        viewModel.onRecapRequested()

        val sheet = viewModel.uiState.value.sheet
        assertIs<RecapSheetState.Digest>(sheet)
        // Displayed articles first, in screen order; the rest after, in
        // cache order.
        assertEquals(listOf("Titre 3", "Titre 1", "Titre 2"), sheet.items.map { it.title })
    }

    @Test
    fun aDisplayedArticleDeepInTheCacheStillComesFirst() = runTest {
        // Regression: the pool used to be six articles in the cache's own
        // order, so a screen-first article beyond it missed the batch.
        articles.unreadInCache = (1L..30L).map { article(id = it, title = "Titre $it") }
        generator.chunks = listOf("1. R1")

        val viewModel = viewModel()
        viewModel.onDisplayedOrderChanged(listOf(ArticleId(30L)))
        viewModel.onRecapRequested()

        val sheet = viewModel.uiState.value.sheet
        assertIs<RecapSheetState.Digest>(sheet)
        assertEquals("Titre 30", sheet.items.first().title)
    }

    @Test
    fun readArticlesOnScreenAreSummarizedInTheirPlace() = runTest {
        // The author's ruling after two read articles sat above the first
        // summary: the recap covers the list as displayed, read included.
        articles.cachedArticles.value = listOf(
            article(id = 1, title = "Lu en tête", isRead = true),
            article(id = 2, title = "Non lu ensuite"),
        )
        articles.unreadInCache = listOf(article(id = 2, title = "Non lu ensuite"))
        generator.chunks = listOf("1. R1\n2. R2")

        val viewModel = viewModel()
        viewModel.onDisplayedOrderChanged(listOf(ArticleId(1L), ArticleId(2L)))
        viewModel.onRecapRequested()

        val sheet = viewModel.uiState.value.sheet
        assertIs<RecapSheetState.Digest>(sheet)
        assertEquals(listOf("Lu en tête", "Non lu ensuite"), sheet.items.map { it.title })
    }

    @Test
    fun summarizingMarksTheStillUnreadArticlesAsRead() = runTest {
        articles.cachedArticles.value = listOf(
            article(id = 1, title = "Déjà lu", isRead = true),
            article(id = 2, title = "Non lu"),
        )
        generator.chunks = listOf("1. R1\n2. R2")

        val viewModel = viewModel()
        viewModel.onDisplayedOrderChanged(listOf(ArticleId(1L), ArticleId(2L)))
        viewModel.onRecapRequested()

        // Reading the summary is reading the article; a read one is not
        // re-marked, that would enqueue a useless transmission.
        assertEquals(listOf(setOf(ArticleId(2L))), readSync.markCalls)
    }

    @Test
    fun reopeningTheSheetStartsAFreshRecap() = runTest {
        articles.unreadInCache = listOf(article(title = "Le seul titre"))
        generator.chunks = listOf("1. R1")

        val viewModel = viewModel()
        viewModel.onRecapRequested()
        viewModel.onSheetDismissed()
        viewModel.onRecapRequested()

        val sheet = viewModel.uiState.value.sheet
        assertIs<RecapSheetState.Digest>(sheet)
        assertEquals(1, sheet.items.size)
        assertEquals(2, generator.receivedPrompts.size)
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
