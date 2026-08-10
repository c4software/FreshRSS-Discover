package fr.vbrosseau.freshrssdiscover.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.CACHED_FEED_LIMIT
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.read.ReadDetector
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Le moteur du flux, commun aux modes Liste et Balayage (SPECS.md §4.8).
 *
 * SPECS.md §4.8 dit que les deux modes portent le même contenu, les mêmes
 * règles de lecture et de chargement — seule la présentation change. Ce moteur
 * en est la traduction : pagination, rechargement, amorçage par le cache,
 * marquage et avis vivent une seule fois ici, et chaque mode ne fournit que sa
 * **projection** ([project]), c'est-à-dire la longueur de son extrait.
 *
 * Deux ViewModels jumeaux ont existé, transitions comprises, au motif que la
 * garde à mutualiser était petite. La divergence promise par ARCHITECTURE.md
 * §9.6 est arrivée deux fois : le `loadMore` du Balayage sans la garde
 * `isRefreshing` (repris en GOAL-028), puis un rechargement du Balayage qui
 * projetait des extraits à la longueur de la Liste. Chaque correctif devait
 * être porté deux fois, avec deux jeux de tests.
 *
 * Une classe de base et non un objet délégué : les huit gestes publics de
 * l'écran sont le moteur lui-même, et une délégation méthode à méthode ne
 * ferait que les recopier dans chaque mode.
 */
abstract class FeedSessionViewModel(
    private val articleRepository: ArticleRepository,
    private val readSyncRepository: ReadSyncRepository,
    settingsRepository: SettingsRepository,
    freshnessRepository: FeedFreshnessRepository,
    private val clock: Clock,
    /** Projection d'un article du domaine vers sa carte — le seul point qui distingue les modes. */
    private val project: ArticleProjection,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

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
     * Génération du parcours de pagination (GOAL-028).
     *
     * Le rechargement l'incrémente ; une page revenue d'une génération
     * antérieure est **jetée à l'arrivée** — ni état, ni curseur, ni phase.
     * Sans cela, une page demandée avant le tirage et revenue après lui
     * s'ajoutait sous la liste rafraîchie et **écrasait le curseur du
     * rechargement** : la pagination reprenait en silence le parcours
     * abandonné. Depuis GOAL-027, son écriture au cache réinsérait de surcroît
     * des lignes que `retainOnly` venait d'emporter.
     *
     * Un compteur et non une attente sur [isLoading] : faire patienter le geste
     * derrière une requête lente serait l'inverse de ce qu'un rechargement
     * promet. La page périmée est jetée même si le rechargement **échoue**
     * ensuite — le geste a désavoué l'ancien parcours, et « Réessayer » est le
     * chemin du retour.
     */
    private var loadGeneration = 0

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
     * les articles déjà signalés — n'a de sens que pour **cette** session de
     * lecture. Une instance partagée par Hilt survivrait à l'écran et
     * signalerait comme lus des articles d'une session précédente. C'est aussi
     * lui qui rend le retour en arrière inoffensif en Balayage (GOAL-012-T04) :
     * un article déjà signalé ne l'est jamais deux fois.
     *
     * **Reconstruit à chaque changement de réglages**, ce qui remet ses
     * chronomètres à zéro. C'est voulu : un seuil modifié en cours de lecture ne
     * doit pas s'appliquer à un chronomètre démarré sous l'ancien. Les articles
     * déjà signalés sont oubliés du même coup, mais sans conséquence — ils sont
     * déjà `isRead` localement, et [markRead] les écarte avant transmission.
     *
     * **`null` quand le marquage automatique est éteint** (SPECS.md §4.5) :
     * l'absence de détecteur dit mieux qu'un booléen à côté qu'il n'y a rien à
     * alimenter — on ne peut pas oublier de le consulter.
     */
    private var readDetector: ReadDetector? = ReadDetector(clock)

    /** Articles déjà transmis au dépôt de synchronisation, sur la vie de l'écran. */
    private val alreadyReported = mutableSetOf<ArticleId>()

    /**
     * Vrai une fois la décision d'amorçage prise : elle ne se prend qu'une fois.
     *
     * SPECS.md §5.1 : le lancement montre le cache et **s'y arrête**. La seule
     * exception est un cache vide — première ouverture, retour après
     * déconnexion — où ne rien demander laisserait une application morte. La
     * décision se prend sur la **première** émission du cache, jamais après :
     * une écriture ultérieure qui viderait la liste ne doit pas déclencher de
     * requête dans le dos de l'utilisateur.
     */
    private var hasDecidedBootstrap = false

    init {
        /*
         * Les seuils viennent des réglages, pas de constantes compilées : une
         * modification s'applique **sans redémarrage**, ce pour quoi le dépôt
         * expose un flux plutôt qu'une lecture ponctuelle (SPECS.md §6).
         *
         * L'interrupteur du marquage automatique emprunte le **même** chemin :
         * ouvrir un second flux ferait observer deux sources à qui n'applique
         * qu'une règle, et l'extinction n'arriverait pas forcément au même
         * moment que le seuil.
         */
        settingsRepository.observeReadingSettings()
            .onEach { settings ->
                readDetector = if (settings.autoMarkAsReadEnabled) {
                    ReadDetector(
                        clock = clock,
                        visibleFractionThreshold = settings.visibleFraction,
                        continuousVisibilityMillis = settings.continuousVisibilityMillis,
                    )
                } else {
                    null
                }
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
                if (!hasServerContent) {
                    _uiState.update { it.merging(cached, now, atHead = false, project = project) }
                }
                if (!hasDecidedBootstrap) {
                    hasDecidedBootstrap = true
                    if (cached.isEmpty()) load() else _uiState.update { it.settledFromCache() }
                }
            }
            .launchIn(viewModelScope)

        staleness.isStale
            .onEach { isStale -> _uiState.update { it.copy(isStaleNoticeAvailable = isStale) } }
            .launchIn(viewModelScope)
    }

    /**
     * L'écran vient au premier plan (SPECS.md §5.1, GOAL-025).
     *
     * **Un écran sans article interroge le serveur.** SPECS.md §5.1 dit qu'aucune
     * requête ne part tant qu'il y a quelque chose à montrer ; sa réciproque
     * n'était appliquée qu'une fois, au premier échantillon du cache
     * ([hasDecidedBootstrap]). Tout lire puis revenir laissait donc un écran
     * vide et muet, dont on ne sortait qu'en trouvant le bouton de la barre de
     * titre.
     *
     * **Accroché à la venue au premier plan, jamais à l'état de vide.** Un
     * serveur qui n'a rien à rendre laisse l'écran vide : réagir à cet état le
     * ferait redemander sans fin. Ici, arriver sur le flux, revenir des
     * réglages, sortir de veille valent chacun **une** tentative.
     *
     * [refresh] et non [load] : le curseur d'une fin de flux ne mène nulle part,
     * et le rechargement est le seul chemin qui transmette les marquages en
     * attente avant d'interroger (GOAL-024) — ce qui est exactement la
     * situation, puisqu'on arrive sur un écran vide en venant de tout lire.
     *
     * Les phases écartées le sont chacune pour sa raison : un premier
     * chargement est déjà en vol — c'est le cas du démarrage à cache vide, où
     * doubler la requête serait le défaut le plus facile à commettre ici — un
     * échec porte déjà sa reprise et la relancer à chaque retour martèlerait un
     * réseau absent, et une session terminée est sur le point de rendre l'écran.
     */
    fun onScreenShown() {
        val state = _uiState.value
        if (state.articles.isNotEmpty() || state.isRefreshing) return
        if (state.phase != DiscoverPhase.Idle && state.phase != DiscoverPhase.EndOfFeed) return

        refresh()
    }

    /**
     * Demande la page suivante.
     *
     * Idempotente et sans effet hors du cas utile : la fin de flux, un
     * chargement en cours, un rafraîchissement, une erreur non acquittée et une
     * session terminée l'ignorent. C'est ce qui permet à l'écran de l'appeler
     * librement — au défilement comme au balayage — sans avoir à savoir ce
     * qu'il en est. La garde `isRefreshing` est celle que le Balayage avait
     * perdue (GOAL-028) : sans elle, une page pouvait **démarrer** pendant le
     * rechargement, avec le curseur de l'ancien parcours.
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
     * Recharge le flux depuis le début (SPECS.md §4.6) — le tirage en Liste, le
     * bouton en Balayage.
     *
     * Le geste **vide** l'affichage et repart du début : la liste est
     * remplacée, pas complétée, et l'écran revient en tête. Ce que
     * l'utilisateur regardait disparaît — c'est le prix d'un geste qui a un
     * effet immédiat et lisible, assumé dans la spécification.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        // Le geste désavoue le parcours en cours : toute page encore en vol
        // appartient désormais à une génération périmée (GOAL-028).
        loadGeneration++
        _uiState.update { it.copy(isRefreshing = true) }

        viewModelScope.launch {
            /*
             * **Dire au serveur ce qui a été lu, avant de lui demander la
             * suite.** Sans cela, le rechargement interroge un serveur qui
             * ignore encore les lectures des dernières secondes : le
             * regroupement des marquages retient jusqu'à 5 s (SPECS.md §8,
             * question 4). Le serveur renvoie donc ces articles comme non lus,
             * ils reprennent leur place dans la page de 40, et les nouveaux
             * n'apparaissent pas — **il faut recharger une seconde fois**.
             *
             * Signalé par l'auteur, puis mesuré sur l'émulateur : 162 non lus
             * avant lecture, 162 encore à l'instant du rechargement, 158 douze
             * secondes plus tard.
             *
             * L'attente est délibérée, et c'est le seul endroit du code où
             * `flush` est **attendu** avant autre chose : ailleurs il part sans
             * qu'on en guette l'issue, le marquage étant optimiste. Ici son
             * résultat conditionne la justesse de ce que le serveur va rendre.
             * Un échec ne bloque rien pour autant — la file conserve, et le
             * rechargement a lieu quand même, comme avant.
             */
            readSyncRepository.flush()

            when (val result = articleRepository.refresh()) {
                is Outcome.Success -> {
                    cursor = result.value.nextCursor
                    hasServerContent = true
                    _uiState.update { it.refreshedWith(result.value, clock.nowEpochMillis(), project) }
                }

                is Outcome.Failure -> _uiState.update { it.failedWith(result.error) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Prend en compte une observation de la visibilité.
     *
     * Appelée périodiquement par l'écran, **y compris quand rien ne bouge** :
     * la règle de SPECS.md §4.5 porte sur une **durée continue**, et le
     * détecteur étant pur, cette durée ne s'écoule que d'une observation à
     * l'autre. En Balayage, c'est le piège d'intégration principal — un article
     * plein écran immobile ne produit aucun événement, et sans seconde
     * observation il ne serait jamais signalé.
     *
     * **Sans effet quand le marquage automatique est éteint** : il n'y a alors
     * pas de détecteur à alimenter. L'ouverture d'un article, elle, continue de
     * le marquer lu (SPECS.md §4.7) — voir [onArticleOpened].
     */
    fun onVisibilityChanged(visibility: Map<ArticleId, Float>) {
        val justRead = readDetector?.onVisibilityChanged(visibility).orEmpty()
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
     * **Y compris marquage automatique éteint** : l'interrupteur de SPECS.md
     * §4.5 n'arrête que la détection par visibilité. Le faire porter aussi sur
     * l'ouverture confondrait un geste délibéré avec un effet du défilement.
     *
     * **Faux hors ligne** (SPECS.md §5.2) : l'onglet personnalisé n'afficherait
     * que la page d'erreur du navigateur, sans dire pourquoi, et l'article
     * passerait pour lu sans avoir pu l'être. Un avis explicite est publié à la
     * place, et rien n'est marqué.
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
     * L'acquittement va au dépôt partagé : il vaut pour les deux modes à la
     * fois, et il expirera de lui-même au prochain contact avec le serveur.
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

        // Relevée au départ, comparée à l'arrivée : si un rechargement est
        // passé entre les deux, cette page décrit un flux qui n'existe plus.
        val generation = loadGeneration

        viewModelScope.launch {
            val result = articleRepository.loadPage(cursor)
            // Relâché **avant** le traitement : une page entièrement déjà
            // affichée enchaîne sur la suivante, et le verrou encore posé
            // ferait de cet enchaînement un appel sans effet.
            isLoading = false

            // Jetée entière, échec compris : signaler l'échec d'une requête
            // désavouée peindrait en panne un flux qui vient d'être remplacé.
            if (generation != loadGeneration) return@launch

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
            state.merging(page.articles, now, atHead = !hasServerContent, project = project)
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
