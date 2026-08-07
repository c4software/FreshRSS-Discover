package fr.vbrosseau.freshrssdiscover.presentation.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.CACHED_FEED_LIMIT
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.read.ReadDetector
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.toUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Le flux, un article par écran (SPECS.md §4.8).
 *
 * Les règles sont celles du mode Liste — mêmes pages, même marquage, même fin
 * de flux explicite — et c'est pourquoi ce ViewModel réutilise `ReadDetector`
 * et `DiscoverPhase` sans les adapter. Ce qui change tient en une phrase : la
 * source d'observation de la visibilité n'est plus une disposition de liste
 * mais la position du balayage, et elle est calculée par [pagerVisibility].
 */
@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val readSyncRepository: ReadSyncRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SwipeUiState())
    val uiState: StateFlow<SwipeUiState> = _uiState.asStateFlow()

    /** Position atteinte dans le flux. `null` demande le début, et seulement `null`. */
    private var cursor: PageCursor? = null

    /**
     * Verrou d'un chargement en vol.
     *
     * Le chargement anticipé est réévalué à chaque recomposition du pagineur :
     * attendre que `LoadingMore` soit visible dans le `StateFlow` laisserait
     * passer plusieurs requêtes avant la première mise à jour.
     */
    private var isLoading: Boolean = false

    /** Vrai dès qu'une page du serveur a été fondue : le cache cesse alors d'alimenter. */
    private var hasServerContent: Boolean = false

    /**
     * Décide de la lecture à partir des observations de visibilité.
     *
     * Construit ici plutôt qu'injecté : son état — chronomètres en cours et
     * articles déjà signalés — n'a de sens que pour cette session de lecture.
     * C'est **lui** qui rend le retour en arrière inoffensif (GOAL-012-T04) :
     * un article déjà signalé ne l'est jamais deux fois, quel que soit le
     * nombre de fois qu'on repasse dessus.
     */
    private var readDetector = ReadDetector(clock)

    /** Articles déjà transmis au dépôt de synchronisation, sur la vie de l'écran. */
    private val alreadyReported = mutableSetOf<ArticleId>()

    init {
        // Les seuils viennent des réglages (SPECS.md §6) : une modification
        // s'applique sans redémarrage, d'où un flux plutôt qu'une lecture.
        settingsRepository.observeReadingSettings()
            .onEach { settings ->
                readDetector = ReadDetector(
                    clock = clock,
                    visibleFractionThreshold = settings.visibleFraction,
                    continuousVisibilityMillis = settings.continuousVisibilityMillis,
                )
            }
            .launchIn(viewModelScope)

        // Ce qui n'a pas pu partir à la session précédente repart ici.
        viewModelScope.launch { readSyncRepository.flush() }

        /*
         * Souscrit **avant** le premier chargement : SPECS.md §5.1 veut que le
         * flux montre son contenu sans attendre le réseau. En plein écran le
         * défaut serait plus visible encore qu'en liste — un unique écran vide,
         * sans rien à balayer, pendant toute la durée de la requête.
         */
        articleRepository.observeCachedArticles(CACHED_FEED_LIMIT)
            .onEach { cached ->
                val now = clock.nowEpochMillis()
                if (!hasServerContent) _uiState.update { it.merging(cached, now, atHead = false) }
            }
            .launchIn(viewModelScope)

        load()
    }

    /**
     * Demande la page suivante (SPECS.md §4.4, GOAL-012-T02).
     *
     * Idempotente et sans effet hors du cas utile : c'est ce qui permet à
     * l'écran de l'appeler dès que le balayage approche du dernier article
     * chargé, sans avoir à savoir ce qu'il en est du chargement précédent.
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
     * Recharge le flux depuis le début (SPECS.md §4.6).
     *
     * Même effet qu'en mode Liste, **et c'est délibéré** : la pile est
     * remplacée, pas complétée, et le balayage revient à la première carte. Un
     * rechargement qui ajouterait en tête sans y ramener serait invisible en
     * plein écran — l'utilisateur resterait sur la carte qu'il regardait, sans
     * rien voir se produire.
     *
     * Ce que l'écran regardait disparaît donc, comme au tirage : c'est le prix
     * d'une action qui a un effet immédiat et lisible, assumé par la
     * spécification.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            when (val result = articleRepository.refresh()) {
                is Outcome.Success -> onRefreshed(result.value)
                is Outcome.Failure -> _uiState.update { it.failedWith(result.error) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Prend en compte une observation de la visibilité.
     *
     * Appelée périodiquement par l'écran, **y compris quand rien ne bouge** :
     * un article plein écran immobile ne produit aucun événement, et la règle
     * de SPECS.md §4.5 porte sur une durée. C'est le piège d'intégration
     * principal de ce mode — un article regardé dix secondes ne serait jamais
     * signalé, faute d'une seconde observation.
     */
    fun onVisibilityChanged(visibility: Map<ArticleId, Float>) {
        val justRead = readDetector.onVisibilityChanged(visibility)
        if (justRead.isEmpty()) return

        markRead(justRead)
    }

    /**
     * Ouvre un article : le marque comme lu, et dit si l'ouverture peut avoir
     * lieu.
     *
     * Mêmes règles qu'en mode Liste (SPECS.md §4.7 et §5.2) : lu quelle que
     * soit sa visibilité passée, refusé hors ligne où l'onglet personnalisé
     * n'afficherait que la page d'erreur du navigateur.
     *
     * @return vrai si l'appelant doit ouvrir le lien.
     */
    fun onArticleOpened(articleId: Long): Boolean {
        if (_uiState.value.isOffline) {
            _uiState.update { it.copy(isOfflineOpenNoticeVisible = true) }
            return false
        }

        markRead(setOf(ArticleId(articleId)))
        return true
    }

    /** Acquitte l'avis d'ouverture impossible : il a été lu, il disparaît. */
    fun dismissOfflineOpenNotice() {
        _uiState.update { it.copy(isOfflineOpenNoticeVisible = false) }
    }

    /**
     * Marque localement, puis transmet ce qui ne l'a pas déjà été.
     *
     * L'état local passe à « lu » immédiatement (marquage optimiste,
     * SPECS.md §4.5) et **ne revient jamais en arrière** : c'est la moitié
     * visible de GOAL-012-T04, l'autre étant la mémoire du détecteur.
     */
    private fun markRead(ids: Set<ArticleId>) {
        _uiState.update { it.markingRead(ids) }

        val unreported = ids - alreadyReported
        if (unreported.isEmpty()) return

        alreadyReported += unreported
        viewModelScope.launch { readSyncRepository.markAsRead(unreported) }
    }

    private fun load() {
        if (isLoading) return
        isLoading = true

        val isFirstPage = cursor == null
        _uiState.update {
            it.copy(phase = if (isFirstPage) DiscoverPhase.InitialLoading else DiscoverPhase.LoadingMore)
        }

        viewModelScope.launch {
            val result = articleRepository.loadPage(cursor)
            // Relâché avant le traitement : une page entièrement déjà affichée
            // enchaîne sur la suivante, et le verrou encore posé ferait de cet
            // enchaînement un appel sans effet.
            isLoading = false

            when (result) {
                is Outcome.Success -> onPageLoaded(result.value)
                is Outcome.Failure -> _uiState.update { it.failedWith(result.error) }
            }
        }
    }

    /**
     * Remplace la pile par la page qui vient d'arriver.
     *
     * Rien n'est remis à zéro, et il faut le dire parce que ce serait le
     * réflexe : ni le détecteur de lecture, dont `onVisibilityChanged` écarte
     * de lui-même les chronomètres des articles absents de l'observation
     * suivante ; ni `alreadyReported`, qui retient ce qui est déjà parti au
     * serveur — ce qu'un rechargement ne change pas.
     */
    private fun onRefreshed(page: ArticlePage) {
        val now = clock.nowEpochMillis()

        cursor = page.nextCursor
        hasServerContent = true

        _uiState.update { state ->
            state.copy(
                articles = page.articles.map { article -> article.toUiModel(now) },
                phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
                isOffline = false,
            )
        }
    }

    private fun onPageLoaded(page: ArticlePage) {
        val shownBefore = _uiState.value.articles.size
        val now = clock.nowEpochMillis()

        _uiState.update { state ->
            // La première page du serveur arrive par-dessus le cache : ce sont
            // les mêmes articles à quelques nouveautés près, et poser celles-ci
            // en fin de balayage les montrerait très loin de leur date.
            state.merging(page.articles, now, atHead = !hasServerContent)
                .copy(
                    phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
                    isOffline = false,
                )
        }

        hasServerContent = true
        cursor = page.nextCursor

        /*
         * Une page dont tout était déjà affiché ne fait rien grandir. S'arrêter
         * là livrerait un balayage qui bute sur la page de fin sans que le flux
         * soit terminé (SPECS.md §4.4) : on enchaîne donc sur la suivante,
         * seule à pouvoir apporter du nouveau.
         */
        if (_uiState.value.articles.size == shownBefore && page.hasMore) load()
    }
}
