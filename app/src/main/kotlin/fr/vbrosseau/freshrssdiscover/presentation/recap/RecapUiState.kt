package fr.vbrosseau.freshrssdiscover.presentation.recap

/**
 * State of the recap surface.
 *
 * [isModelUsable] is what makes the title-bar button dynamic: `false` until
 * the platform has answered, so the button never flashes on a device that
 * turns out unsupported. It stays `true` when the model is merely not
 * downloaded yet — the capability exists, the sheet will offer the download.
 *
 * [isSheetOpen] belongs here rather than in the Composable: the request
 * comes from the title bar while the sheet lives on the feed, and only the
 * ViewModel is visible from both.
 */
data class RecapUiState(
    val isModelUsable: Boolean = false,
    val isSheetOpen: Boolean = false,
)
