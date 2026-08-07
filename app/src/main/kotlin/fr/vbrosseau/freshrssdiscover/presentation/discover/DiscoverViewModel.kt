package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.read.ReadDetector
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val readSyncRepository: ReadSyncRepository,
    settingsRepository: SettingsRepository,
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

    /**
     * Décide de la lecture à partir des observations de visibilité.
     *
     * Construit ici plutôt qu'injecté : son état — les chronomètres en cours et
     * les articles déjà signalés — n'a de sens que pour **cette** liste. Une
     * instance partagée par Hilt survivrait à l'écran et signalerait comme lus
     * des articles d'une session précédente.
     *
     * **Reconstruit à chaque changement de réglages**, ce qui remet ses
     * chronomètres à zéro. C'est voulu : un seuil modifié en cours de lecture ne
     * doit pas s'appliquer à un chronomètre démarré sous l'ancien. Les articles
     * déjà signalés sont oubliés du même coup, mais sans conséquence — ils sont
     * déjà `isRead` localement, et [reportRead] les écarte avant transmission.
     */
    private var readDetector = ReadDetector(clock)

    init {
        /*
         * Les seuils viennent des réglages, pas de constantes compilées : une
         * modification s'applique **sans redémarrage**, ce pour quoi le dépôt
         * expose un flux plutôt qu'une lecture ponctuelle (SPECS.md §6).
         */
        settingsRepository.observeReadingSettings()
            .onEach { settings ->
                readDetector = ReadDetector(
                    clock = clock,
                    visibleFractionThreshold = settings.visibleFraction,
                    continuousVisibilityMillis = settings.continuousVisibilityMillis,
                )
            }
            .launchIn(viewModelScope)

        /*
         * Rejeu au démarrage : ce qui n'a pas pu partir à la session précédente
         * — application fermée hors ligne, notamment — repart ici. Sans cela,
         * un marquage attendrait la prochaine lecture pour être transmis
         * (SPECS.md §4.5).
         */
        viewModelScope.launch { readSyncRepository.flush() }

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

    /**
     * Prend en compte une observation de la visibilité de la liste.
     *
     * Appelée périodiquement par l'écran, y compris quand rien ne bouge : la
     * règle de SPECS.md §4.5 porte sur une **durée continue**, et le détecteur
     * étant pur, cette durée ne s'écoule que d'une observation à l'autre.
     *
     * L'état local passe à « lu » immédiatement, sans attendre le serveur
     * (marquage optimiste, SPECS.md §4.5) — et l'article **reste à sa place**,
     * seul son drapeau change : le retirer déplacerait le contenu sous le doigt.
     */
    /**
     * Transmet un lot de marquages, en écartant ce qui l'a déjà été.
     *
     * Le filtre couvre la reconstruction du détecteur à un changement de
     * réglages : il oublie alors les articles déjà signalés, et les
     * resignalerait au prochain échantillon. Le marquage distant est idempotent,
     * mais la requête, elle, serait inutile.
     *
     * `flush` suit immédiatement `markAsRead` : le marquage est optimiste, donc
     * l'état local est déjà à jour et l'échec éventuel de la transmission ne se
     * voit pas — la file le conservera pour plus tard.
     */
    private fun reportRead(ids: Set<ArticleId>) {
        val unreported = ids - alreadyReported
        if (unreported.isEmpty()) return

        alreadyReported += unreported
        viewModelScope.launch {
            readSyncRepository.markAsRead(unreported)
            readSyncRepository.flush()
        }
    }

    /** Articles déjà transmis au dépôt de synchronisation, sur la vie de l'écran. */
    private val alreadyReported = mutableSetOf<ArticleId>()

    fun onVisibilityChanged(visibility: Map<ArticleId, Float>) {
        val justRead = readDetector.onVisibilityChanged(visibility)
        if (justRead.isEmpty()) return

        val readIds = justRead.mapTo(mutableSetOf()) { it.value }
        _uiState.update { state ->
            state.copy(
                articles = state.articles.map { if (it.id in readIds) it.copy(isRead = true) else it },
            )
        }
        reportRead(justRead)
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
