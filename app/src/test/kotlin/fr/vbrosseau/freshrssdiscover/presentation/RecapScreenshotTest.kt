package fr.vbrosseau.freshrssdiscover.presentation

import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSheetContent
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSheetState
import org.junit.Test

/**
 * Visual references for the recap sheet content (SPECS.md §4.10).
 *
 * The content, not the modal: `ModalBottomSheet` renders in its own window,
 * out of the capture's reach. What can silently rot is here anyway — the
 * digest typography, the download offer's button, both against the sheet
 * background in dark theme.
 */
class RecapScreenshotTest : ScreenshotTest() {

    @Test
    fun recapSheetWithADigest() {
        capture("recap-digest") {
            RecapSheetContent(
                state = RecapSheetState.Digest(
                    text = "• L'actualité Android tient en deux annonces, dont une sur l'IA locale.\n" +
                        "• Trois flux parlent du même procès, sans verdict encore.\n" +
                        "• Le reste est plus léger : deux tests de claviers et une rétrospective.",
                    isGenerating = false,
                ),
                onDownloadConfirm = {},
            )
        }
    }

    @Test
    fun recapSheetOfferingTheDownload() {
        capture("recap-telechargement-propose") {
            RecapSheetContent(
                state = RecapSheetState.DownloadOffer,
                onDownloadConfirm = {},
            )
        }
    }
}
