package fr.vbrosseau.freshrssdiscover.presentation

import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSegmentUi
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSheetContent
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapSheetState
import org.junit.Test

/**
 * Visual references for the recap sheet content (SPECS.md §4.10).
 *
 * The content, not the modal: `ModalBottomSheet` renders in its own window,
 * out of the capture's reach. What can silently rot is here anyway — the
 * underlined passages against the plain prose, the download offer's button,
 * both in dark theme.
 */
class RecapScreenshotTest : ScreenshotTest() {

    @Test
    fun recapSheetWithABrief() {
        capture("recap-digest") {
            RecapSheetContent(
                state = RecapSheetState.Digest(
                    segments = listOf(
                        RecapSegmentUi(text = "La semaine s'ouvre sur la bêta publique de ", url = null),
                        RecapSegmentUi(text = "GNOME 51", url = "https://exemple.org/gnome"),
                        RecapSegmentUi(
                            text = ", pendant que deux articles se répondent autour du ",
                            url = null,
                        ),
                        RecapSegmentUi(text = "Tensor G6", url = "https://exemple.org/tensor"),
                        RecapSegmentUi(
                            text = " : ses gains tiennent à l'efficacité énergétique plus qu'à la " +
                                "puissance, ce que le test long terme du ",
                            url = null,
                        ),
                        RecapSegmentUi(text = "Find X9 Pro", url = "https://exemple.org/oppo"),
                        RecapSegmentUi(text = " confirme en creux.", url = null),
                    ),
                    isGenerating = false,
                    canLoadMore = true,
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
