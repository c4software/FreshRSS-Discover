package fr.vbrosseau.freshrssdiscover.presentation.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.recap.RECAP_MAX_ARTICLES
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapAvailability
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapDownloadEvent
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapGenerator
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapPrompt
import fr.vbrosseau.freshrssdiscover.domain.recap.parseRecapLines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the on-device recap (SPECS.md §4.10).
 *
 * Availability is asked once at construction: the ViewModel lives with the
 * feed destination, so every arrival on the feed asks again, which is the
 * cadence the port documents. The one transition that happens while the
 * screen is up — the model finishing its download — goes through this
 * ViewModel too, so it moves on by itself rather than re-asking.
 *
 * No business logic here: what the prompt says is `RecapPrompt`'s, what the
 * device can do is the generator's; this class only sequences sheet states.
 */
@HiltViewModel
class RecapViewModel @Inject constructor(
    private val generator: RecapGenerator,
    private val articleRepository: ArticleRepository,
    private val language: RecapLanguage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecapUiState())
    val uiState: StateFlow<RecapUiState> = _uiState

    private var availability = RecapAvailability.Unavailable

    /**
     * The running download or generation. Dismissing the sheet cancels it:
     * inference holds the NPU, and nobody reads a digest behind a closed
     * sheet.
     */
    private var work: Job? = null

    init {
        viewModelScope.launch {
            availability = generator.availability()
            _uiState.update { it.copy(isModelUsable = availability != RecapAvailability.Unavailable) }
        }
    }

    /** The title-bar button. Re-tapping while open restarts nothing. */
    fun onRecapRequested() {
        if (_uiState.value.sheet != RecapSheetState.Hidden) return

        when (availability) {
            RecapAvailability.Available -> generate()
            RecapAvailability.Downloadable,
            RecapAvailability.Downloading,
            -> _uiState.update { it.copy(sheet = RecapSheetState.DownloadOffer) }
            // The button is not published in this state; a race with a
            // just-lost availability simply keeps the sheet closed.
            RecapAvailability.Unavailable -> Unit
        }
    }

    /** The sheet's download button, both first offer and retry. */
    fun onDownloadConfirmed() {
        _uiState.update { it.copy(sheet = RecapSheetState.Downloading(totalBytesDownloaded = 0L)) }
        work = viewModelScope.launch {
            generator.download().collect { event ->
                when (event) {
                    is RecapDownloadEvent.Progress ->
                        _uiState.update { it.copy(sheet = RecapSheetState.Downloading(event.totalBytesDownloaded)) }
                    RecapDownloadEvent.Completed -> {
                        availability = RecapAvailability.Available
                        generate()
                    }
                    RecapDownloadEvent.Failed ->
                        _uiState.update { it.copy(sheet = RecapSheetState.DownloadFailed) }
                }
            }
        }
    }

    /** Swipe-down or back on the sheet. Reopening starts a fresh recap. */
    fun onSheetDismissed() {
        work?.cancel()
        work = null
        summarizedIds.clear()
        _uiState.update { it.copy(sheet = RecapSheetState.Hidden) }
    }

    /** The "load more" pill: summarizes the next batch of unread articles. */
    fun onLoadMore() {
        val sheet = _uiState.value.sheet
        if (sheet !is RecapSheetState.Digest || sheet.isGenerating) return

        generate()
    }

    /**
     * Identity, not position: `unreadFromCache` serves a shuffled list, so
     * the next batch is "unread minus already summarized", never "the next
     * five rows".
     */
    private val summarizedIds = mutableSetOf<ArticleId>()

    /**
     * The feed as the user sees it, published by the displayed mode. The
     * recap follows this order, not the cache's own shuffle: summarizing in
     * an order the screen contradicts would read as random.
     */
    private var displayedOrder: List<ArticleId> = emptyList()

    fun onDisplayedOrderChanged(ids: List<ArticleId>) {
        displayedOrder = ids
    }

    private fun generate() {
        work = viewModelScope.launch {
            // One extra beyond the batch: its presence is what proves more
            // unread articles remain behind the ones about to be shown.
            val poolSize = summarizedIds.size + RECAP_MAX_ARTICLES + 1
            // Sorting is stable: articles not on screen keep the cache's
            // order among themselves, after every displayed one.
            val rank = displayedOrder.withIndex().associate { it.value to it.index }
            val remaining = articleRepository.unreadFromCache(poolSize)
                .filter { it.id !in summarizedIds }
                .sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            val articles = remaining.take(RECAP_MAX_ARTICLES)
            if (articles.isEmpty()) {
                if (summarizedIds.isEmpty()) _uiState.update { it.copy(sheet = RecapSheetState.Empty) }
                return@launch
            }
            val canLoadMore = remaining.size > articles.size

            // Each batch replaces the previous cards (author's call on the
            // fifth device run): the sheet is a page of five, not a pile —
            // only the id exclusion above remembers what was already shown.
            fun digest(
                items: List<RecapItemUi>,
                isGenerating: Boolean,
            ) = RecapSheetState.Digest(
                items = items,
                plannedCount = articles.size,
                isGenerating = isGenerating,
                canLoadMore = canLoadMore,
            )

            _uiState.update { it.copy(sheet = digest(emptyList(), isGenerating = true)) }
            var text = ""
            try {
                generator.generate(RecapPrompt.build(articles, language.displayName())).collect { chunk ->
                    text += chunk
                    _uiState.update { it.copy(sheet = digest(itemsOf(text, articles), isGenerating = true)) }
                }
                summarizedIds += articles.map { it.id }
                _uiState.update { it.copy(sheet = digest(itemsOf(text, articles), isGenerating = false)) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // The flow throwing is the adapter's failure contract; what
                // arrived is discarded — half a digest reads as a whole one.
                Timber.w(failure, "Génération du récap échouée")
                _uiState.update { it.copy(sheet = RecapSheetState.GenerationFailed) }
            }
        }
    }
}

/**
 * The model's numbering is what ties a summary back to its article — and to
 * the tap that opens it. A number pointing outside the list keeps its text
 * unlinked, and a model that ignored the format degrades to the raw text
 * whole rather than to a blank sheet.
 */
private fun itemsOf(
    text: String,
    articles: List<Article>,
): List<RecapItemUi> {
    val lines = parseRecapLines(text)
    if (lines.isEmpty()) {
        val raw = text.trim()
        return if (raw.isEmpty()) emptyList() else listOf(RecapItemUi(title = null, summary = raw, url = null))
    }

    return lines.map { line ->
        val article = articles.getOrNull(line.index - 1)
        RecapItemUi(title = article?.title, summary = line.text, url = article?.url)
    }
}
