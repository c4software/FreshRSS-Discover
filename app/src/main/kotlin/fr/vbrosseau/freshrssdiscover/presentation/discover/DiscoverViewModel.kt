package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    /**
     * Position atteinte dans le flux. `null` demande le début — et seulement
     * `null` : fabriquer un curseur vide relancerait la première page sans que
     * rien ne le signale.
     */
    private var cursor: PageCursor? = null

    /**
     * Verrou d'un chargement en vol.
     *
     * Une propriété, et non l'état publié : le défilement demande la page
     * suivante à chaque image, et attendre que `LoadingMore` soit visible dans
     * le `StateFlow` laisserait passer plusieurs requêtes avant la première
     * mise à jour. Le ViewModel n'étant touché que depuis le dispatcher
     * principal, un booléen suffit — il n'y a pas de concurrence à arbitrer.
     */
    private var isLoading: Boolean = false

    init {
        load()
    }

    /**
     * Demande la page suivante.
     *
     * Idempotente et sans effet hors du cas utile : la fin de flux, un
     * chargement en cours, une erreur non acquittée et une session terminée
     * l'ignorent. C'est ce qui permet à l'écran de l'appeler librement pendant
     * le défilement, sans avoir à savoir ce qu'il en est.
     */
    fun loadMore() {
        if (_uiState.value.phase != DiscoverPhase.Idle) return
        load()
    }

    /**
     * Réessaie après un échec.
     *
     * Distincte de [loadMore] : reprendre après une erreur est un geste de
     * l'utilisateur, et le confondre avec le chargement anticipé relancerait la
     * requête en boucle tant que le réseau reste absent.
     */
    fun retry() {
        if (_uiState.value.phase !is DiscoverPhase.Failed) return
        load()
    }

    private fun load() {
        if (isLoading) return
        isLoading = true

        val isFirstPage = cursor == null
        _uiState.update {
            it.copy(phase = if (isFirstPage) DiscoverPhase.InitialLoading else DiscoverPhase.LoadingMore)
        }

        viewModelScope.launch {
            when (val result = articleRepository.loadPage(cursor)) {
                is Outcome.Success -> onPageLoaded(result.value)
                is Outcome.Failure -> onPageFailed(result.error)
            }
            isLoading = false
        }
    }

    private fun onPageLoaded(page: ArticlePage) {
        cursor = page.nextCursor
        val now = clock.nowEpochMillis()

        _uiState.update { state ->
            state.copy(
                articles = state.articles + page.articles.map { it.toUiModel(now) },
                phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
            )
        }
    }

    /**
     * Les articles déjà chargés sont **conservés** (SPECS.md §4.4) : les
     * effacer parce que la page suivante a échoué punirait l'utilisateur de
     * s'être approché du bas du flux.
     */
    private fun onPageFailed(error: FeedError) {
        _uiState.update { state ->
            state.copy(
                phase = when (error) {
                    FeedError.SessionExpired -> DiscoverPhase.SessionEnded
                    FeedError.NoNetwork -> DiscoverPhase.Failed(DiscoverFailure.NoNetwork)
                    FeedError.ServerUnreachable -> DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable)
                    is FeedError.Unexpected -> DiscoverPhase.Failed(DiscoverFailure.Unexpected)
                },
            )
        }
    }
}
