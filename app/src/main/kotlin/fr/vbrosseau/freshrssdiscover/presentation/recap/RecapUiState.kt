package fr.vbrosseau.freshrssdiscover.presentation.recap

/**
 * State of the recap surface.
 *
 * [isModelUsable] is what makes the title-bar button dynamic: `false` until
 * the platform has answered, so the button never flashes on a device that
 * turns out unsupported. It stays `true` when the model is merely not
 * downloaded yet — the capability exists, the sheet offers the download.
 *
 * [sheet] belongs here rather than in the Composable: the request comes from
 * the title bar while the sheet lives on the feed, and only the ViewModel is
 * visible from both.
 */
data class RecapUiState(
    val isModelUsable: Boolean = false,
    val sheet: RecapSheetState = RecapSheetState.Hidden,
)

/**
 * What the recap sheet shows, one state per screenful.
 *
 * The download states exist because the model may not be installed at first
 * use (SPECS.md §4.10): the tap then offers the download instead of failing,
 * and generation follows on its own once the download completes.
 */
sealed interface RecapSheetState {
    data object Hidden : RecapSheetState

    /** The model is missing; nothing happens until the user agrees. */
    data object DownloadOffer : RecapSheetState

    /**
     * Bytes rather than a percentage: the platform does not announce the
     * total size, and a made-up denominator would produce a lying bar.
     */
    data class Downloading(val totalBytesDownloaded: Long) : RecapSheetState

    data object DownloadFailed : RecapSheetState

    /** Nothing unread in the cache: there is nothing to summarize. */
    data object Empty : RecapSheetState

    /**
     * The brief's prose as parsed so far, in segments. [isGenerating] tells
     * the sheet the paragraph is still being written — its tail shimmers.
     */
    data class Digest(
        val segments: List<RecapSegmentUi>,
        val isGenerating: Boolean,
        /**
         * Unread articles remain beyond the summarized ones: a "load more"
         * element closes the sheet once generation is done, never during it.
         */
        val canLoadMore: Boolean,
    ) : RecapSheetState

    data object GenerationFailed : RecapSheetState
}

/**
 * One run of the brief. [url] comes from the article the model's marker
 * points to; `null` for connective prose, a marker pointing outside the
 * batch, or a model that dropped the format — readable, just not tappable,
 * like an article without a link in the feed (SPECS.md §4.7).
 */
data class RecapSegmentUi(
    val text: String,
    val url: String?,
)
