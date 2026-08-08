package fr.vbrosseau.freshrssdiscover.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.CACHED_FEED_LIMIT
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.read.ReadDetector
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.feed.FeedStalenessWatcher
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
    freshnessRepository: FeedFreshnessRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    /**
     * Surveille l'ancienneté du flux (SPECS.md §4.6).
     *
     * Construit ici plutôt qu'injecté : il a besoin de [viewModelScope], donc
     * de vivre exactement aussi longtemps que cet écran. Ce qu'il observe, en
     * revanche, est partagé — d'où l'acquittement commun aux deux modes.
     */
    private val staleness = FeedStalenessWatcher(freshnessRepository, clock, viewModelScope)

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
     * Vrai dès qu'une page du serveur a été fondue dans la liste.
     *
     * C'est ce qui referme la porte du cache. Le flux du cache réémet à
     * **chaque écriture**, donc après chaque page reçue : continuer à le
     * consommer ferait réapparaître, au fil de la pagination, des articles dans
     * un ordre que le serveur n'a pas dicté — au milieu d'une liste que
     * l'utilisateur est en train de parcourir. Le cache amorce l'affichage
     * (SPECS.md §5.1), le serveur le poursuit ; jamais les deux à la fois.
     */
    private var hasServerContent: Boolean = false

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

    /** Articles déjà transmis au dépôt de synchronisation, sur la vie de l'écran. */
    private val alreadyReported = mutableSetOf<ArticleId>()

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

        /*
         * Souscrit **avant** le premier `loadPage()`, et l'ordre est tout
         * l'intérêt : SPECS.md §5.1 veut que le flux montre son contenu sans
         * attendre le réseau. Souscrire après, c'est offrir au chargement la
         * possibilité d'aboutir en premier — et un écran vide pendant une
         * requête donne l'impression d'une application sans contenu, alors
         * qu'elle en a.
         */
        articleRepository.observeCachedArticles(CACHED_FEED_LIMIT)
            .onEach { cached ->
                /*
                 * Le cache **amorce** la liste, il ne la remplace jamais.
                 * Réappliquer l'émission entière serait le défaut à ne pas
                 * commettre : le flux réémet à chaque écriture, et l'ordre du
                 * cache n'est pas celui des pages accumulées — la lecture en
                 * cours sauterait. Seuls les articles absents s'ajoutent, à la
                 * suite, sans rien réordonner ni retirer. Passé la première
                 * page du serveur, plus rien ne s'ajoute (voir
                 * [hasServerContent]).
                 */
                val now = clock.nowEpochMillis()
                if (!hasServerContent) _uiState.update { it.merging(cached, now, atHead = false) }
            }
            .launchIn(viewModelScope)

        staleness.isStale
            .onEach { isStale -> _uiState.update { it.copy(isStaleNoticeAvailable = isStale) } }
            .launchIn(viewModelScope)

        load()
    }

    /**
     * Demande la page suivante.
     *
     * Idempotente et sans effet hors du cas utile : la fin de flux, un
     * chargement en cours, un rafraîchissement, une erreur non acquittée et une
     * session terminée l'ignorent. C'est ce qui permet à l'écran de l'appeler
     * librement pendant le défilement, sans avoir à savoir ce qu'il en est.
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.phase != DiscoverPhase.Idle || state.isRefreshing) return
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
     * Tirer-pour-rafraîchir (SPECS.md §4.6).
     *
     * Le geste **vide** l'affichage et repart du début : la liste est
     * remplacée, pas complétée, et l'écran remonte en haut. Ce que
     * l'utilisateur regardait disparaît — c'est le prix d'un geste qui a un
     * effet immédiat et lisible, assumé dans la spécification.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            when (val result = articleRepository.refresh()) {
                is Outcome.Success -> {
                    cursor = result.value.nextCursor
                    hasServerContent = true
                    _uiState.update { it.refreshedWith(result.value, clock.nowEpochMillis()) }
                }

                is Outcome.Failure -> _uiState.update { it.failedWith(result.error) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Prend en compte une observation de la visibilité de la liste.
     *
     * Appelée périodiquement par l'écran, y compris quand rien ne bouge : la
     * règle de SPECS.md §4.5 porte sur une **durée continue**, et le détecteur
     * étant pur, cette durée ne s'écoule que d'une observation à l'autre.
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
     * **Lu quelle que soit sa visibilité passée** (SPECS.md §4.7) : toucher un
     * article est un acte de lecture plus net que n'importe quelle durée
     * d'affichage, et attendre le double seuil ferait revenir dans le flux
     * l'article que l'utilisateur vient précisément d'ouvrir.
     *
     * **Faux hors ligne** (SPECS.md §5.2) : l'onglet personnalisé n'afficherait
     * que la page d'erreur du navigateur, sans dire pourquoi, et l'article
     * passerait pour lu sans avoir pu l'être. Un avis explicite est publié à la
     * place, et rien n'est marqué.
     *
     *
     * @param articleId identifiant sous sa forme brute, celle que la liste
     *   emploie comme clé.
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
     * Fait taire l'invitation à rafraîchir, sans rafraîchir.
     *
     * L'acquittement va au dépôt partagé : il vaut aussi pour le mode Balayage,
     * et il expirera de lui-même au prochain contact avec le serveur.
     */
    fun dismissStaleNotice() {
        staleness.acknowledge()
    }

    /**
     * Marque localement, puis transmet ce qui ne l'a pas déjà été.
     *
     * L'état local passe à « lu » immédiatement, sans attendre le serveur
     * (marquage optimiste, SPECS.md §4.5) — et l'article **reste à sa place**,
     * seul son drapeau change : le retirer déplacerait le contenu sous le doigt.
     *
     * Le filtre de transmission couvre la reconstruction du détecteur à un
     * changement de réglages : il oublie alors les articles déjà signalés, et
     * les resignalerait au prochain échantillon. Le marquage distant est
     * idempotent, mais la requête, elle, serait inutile.
     *
     * `flush` suit immédiatement `markAsRead` : le marquage étant optimiste,
     * l'état local est déjà à jour et l'échec éventuel de la transmission ne se
     * voit pas — la file le conservera pour plus tard.
     */
    private fun markRead(ids: Set<ArticleId>) {
        _uiState.update { it.markingRead(ids) }

        val unreported = ids - alreadyReported
        if (unreported.isEmpty()) return

        alreadyReported += unreported
        viewModelScope.launch {
            readSyncRepository.markAsRead(unreported)
        }
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
            // Relâché **avant** le traitement : une page entièrement déjà
            // affichée enchaîne sur la suivante, et le verrou encore posé
            // ferait de cet enchaînement un appel sans effet.
            isLoading = false

            when (result) {
                is Outcome.Success -> onPageLoaded(result.value)
                is Outcome.Failure -> _uiState.update { it.failedWith(result.error) }
            }
        }
    }

    private fun onPageLoaded(page: ArticlePage) {
        val shownBefore = _uiState.value.articles.size
        val now = clock.nowEpochMillis()

        _uiState.update { state ->
            /*
             * La toute première page arrive **par-dessus** le cache déjà
             * affiché : ce sont les mêmes articles à quelques nouveautés près,
             * et poser celles-ci en bas les montrerait très loin de leur date.
             * Elle se fond donc comme un rafraîchissement — en tête, sans rien
             * réordonner. Les suivantes prolongent le flux : leur place est à
             * la suite.
             */
            state.merging(page.articles, now, atHead = !hasServerContent)
                .copy(
                    phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
                    isOffline = false,
                )
        }

        hasServerContent = true
        cursor = page.nextCursor

        /*
         * Une page dont tout était déjà affiché — le cas ordinaire au
         * lancement, le cache ayant devancé le réseau sur plusieurs pages — ne
         * fait rien grandir. S'arrêter là livrerait une liste qui cesse de
         * s'allonger sans rien dire, indistinguable d'une panne (SPECS.md
         * §4.4) : on enchaîne donc sur la page suivante, seule à pouvoir
         * apporter du nouveau. La suite est finie, le curseur avançant à chaque
         * tour jusqu'à la fin du flux.
         */
        if (_uiState.value.articles.size == shownBefore && page.hasMore) load()
    }
}

/**
 * Remplace la liste par la page rendue, et repart du début.
 *
 * SPECS.md §4.6 : le tirage **vide** l'affichage plutôt que de le compléter.
 * Insérer en tête préservait la lecture, mais laissait le flux s'allonger
 * indéfiniment et rendait le geste presque invisible — on tirait, et rien ne
 * semblait se passer.
 *
 * Les articles déjà signalés comme lus ne sont **pas** oubliés par l'appelant :
 * ils ont bien été transmis au dépôt de synchronisation, et les resignaler
 * ferait une requête pour rien.
 *
 * La phase suit la page rendue : `Idle` s'il reste un curseur, `EndOfFeed`
 * sinon. Elle lève donc aussi l'échec précédent — le réseau vient de répondre —
 * et rouvre un flux qui s'était terminé si le serveur a du neuf.
 */
private fun DiscoverUiState.refreshedWith(
    page: ArticlePage,
    nowEpochMillis: Long,
): DiscoverUiState = copy(
    articles = page.articles.map { article -> article.toUiModel(nowEpochMillis) },
    phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
    isOffline = false,
)

/**
 * Ajoute les articles absents de la liste, sans toucher à ceux qui y sont.
 *
 * Fonction de fichier et non méthode : c'est une transition d'état pure, elle
 * n'a besoin ni du dépôt ni de la portée du ViewModel.
 *
 * @param atHead vrai pour insérer les inconnus **en tête** (SPECS.md §4.6). Ce
 *   qui est déjà affiché n'est jamais réordonné : la règle 3 de SPECS.md §4.2
 *   veut qu'un même ensemble d'articles se présente toujours dans le même
 *   ordre, et un flux qui se réordonne sous le doigt donne le sentiment d'avoir
 *   perdu quelque chose. La position de lecture survit à l'insertion grâce aux
 *   clés stables de la liste, qui repositionnent le premier élément visible sur
 *   sa **clé** et non sur son rang.
 */
private fun DiscoverUiState.merging(
    articles: List<Article>,
    nowEpochMillis: Long,
    atHead: Boolean,
): DiscoverUiState {
    val known = this.articles.mapTo(mutableSetOf(), ArticleUiModel::id)
    val fresh = articles.filterNot { it.id.value in known }.map { it.toUiModel(nowEpochMillis) }
    if (fresh.isEmpty()) return this

    return copy(articles = if (atHead) fresh + this.articles else this.articles + fresh)
}

/** Le drapeau change, l'article ne bouge pas : le retirer déplacerait la lecture. */
private fun DiscoverUiState.markingRead(ids: Set<ArticleId>): DiscoverUiState =
    copy(articles = articles.map { if (ArticleId(it.id) in ids) it.copy(isRead = true) else it })

/**
 * Les articles déjà chargés sont **conservés** (SPECS.md §4.4) : les effacer
 * parce que la page suivante a échoué punirait l'utilisateur de s'être approché
 * du bas du flux. Hors ligne, ils sont même l'essentiel de ce qui reste — d'où
 * le régime signalé en plus de l'échec (SPECS.md §5.2).
 */
private fun DiscoverUiState.failedWith(error: FeedError): DiscoverUiState = copy(
    phase = when (error) {
        FeedError.SessionExpired -> DiscoverPhase.SessionEnded
        FeedError.NoNetwork -> DiscoverPhase.Failed(DiscoverFailure.NoNetwork)
        FeedError.ServerUnreachable -> DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable)
        is FeedError.Unexpected -> DiscoverPhase.Failed(DiscoverFailure.Unexpected)
    },
    isOffline = error == FeedError.NoNetwork,
)
