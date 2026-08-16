package fr.vbrosseau.freshrssdiscover.presentation.recap

import fr.vbrosseau.freshrssdiscover.domain.recap.FakeRecapGenerator
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapAvailability
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule that makes the button dynamic lives here: unusable hides it, and
 * a merely-not-downloaded model still shows it, since the sheet will offer
 * the download.
 */
class RecapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val generator = FakeRecapGenerator()

    @Test
    fun anUnsupportedDeviceKeepsTheButtonHidden() = runTest {
        generator.availability = RecapAvailability.Unavailable

        val viewModel = RecapViewModel(generator)

        assertFalse(viewModel.uiState.value.isModelUsable)
    }

    @Test
    fun anAvailableModelShowsTheButton() = runTest {
        generator.availability = RecapAvailability.Available

        val viewModel = RecapViewModel(generator)

        assertTrue(viewModel.uiState.value.isModelUsable)
    }

    @Test
    fun aDownloadableModelShowsTheButtonToo() = runTest {
        // The capability exists, only the weights are missing: hiding the
        // button here would keep the feature invisible forever, since the
        // download is offered behind it.
        generator.availability = RecapAvailability.Downloadable

        val viewModel = RecapViewModel(generator)

        assertTrue(viewModel.uiState.value.isModelUsable)
    }

    @Test
    fun requestingTheRecapOpensTheSheet() = runTest {
        val viewModel = RecapViewModel(generator)

        viewModel.onRecapRequested()

        assertTrue(viewModel.uiState.value.isSheetOpen)
    }
}
