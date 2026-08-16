package fr.vbrosseau.freshrssdiscover.presentation

import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapItemUi
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSheetContent
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSheetState
import org.junit.Test

/**
 * Visual references for the recap sheet content (SPECS.md §4.10).
 *
 * The content, not the modal: `ModalBottomSheet` renders in its own window,
 * out of the capture's reach. What can silently rot is here anyway — the
 * summary cards against the sheet background, the download offer's button,
 * both in dark theme.
 */
class RecapScreenshotTest : ScreenshotTest() {

    @Test
    fun recapSheetWithADigest() {
        capture("recap-digest") {
            RecapSheetContent(
                state = RecapSheetState.Digest(
                    items = listOf(
                        RecapItemUi(
                            title = "GNOME 51 passe en bêta publique",
                            summary = "L'environnement de bureau ouvre sa bêta, avec des retouches " +
                                "dans la plupart des applications de base.",
                            url = "https://exemple.org/gnome",
                        ),
                        RecapItemUi(
                            title = "Le Tensor G6 décortiqué",
                            summary = "Le nouveau processeur progresse surtout en efficacité " +
                                "énergétique, pas en puissance brute.",
                            url = "https://exemple.org/tensor",
                        ),
                        RecapItemUi(
                            title = "Une rétrospective claviers",
                            summary = "Deux tests de claviers mécaniques et un retour sur dix ans " +
                                "de frappe.",
                            url = "https://exemple.org/claviers",
                        ),
                    ),
                    plannedCount = 3,
                    isGenerating = false,
                ),
                onDownloadConfirm = {},
                onItemClick = {},
            )
        }
    }

    @Test
    fun recapSheetOfferingTheDownload() {
        capture("recap-telechargement-propose") {
            RecapSheetContent(
                state = RecapSheetState.DownloadOffer,
                onDownloadConfirm = {},
                onItemClick = {},
            )
        }
    }
}
