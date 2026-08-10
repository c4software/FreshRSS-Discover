package fr.vbrosseau.freshrssdiscover.presentation.discover

/**
 * Ce que l'écran Discover affiche.
 *
 * Les articles et l'état du chargement sont **séparés** : SPECS.md §4.4 exige
 * qu'un échec de page suivante ne vide pas ce qui est déjà affiché, ce qui
 * serait impossible si la liste ne vivait que dans le cas « chargé » d'un type
 * scellé.
 */
data class DiscoverUiState(
    val articles: List<ArticleUiModel> = emptyList(),
    val phase: DiscoverPhase = DiscoverPhase.InitialLoading,
    /**
     * Rafraîchissement demandé par le geste de tirage (SPECS.md §4.6).
     *
     * Hors de [phase], et c'est délibéré : le tirage se fait **par-dessus** un
     * flux qui a déjà son état — au repos, terminé, en échec — et le fondre
     * dans la phase obligerait à mémoriser celle à laquelle revenir. Les deux
     * indicateurs ne sont d'ailleurs pas au même endroit : l'un en haut, sous
     * le doigt, l'autre en pied de liste.
     */
    val isRefreshing: Boolean = false,
    /**
     * La dernière requête a échoué faute de réseau (SPECS.md §5.2).
     *
     * Distinct de `DiscoverPhase.Failed(NoNetwork)`, qui dit qu'un *chargement*
     * a échoué : celui-ci dit dans quel **régime** est l'application, et c'est
     * lui qui décide du bandeau. Un même échec produit donc deux signaux de
     * portée différente — l'un calme et permanent en tête, l'autre local et
     * accompagné de sa reprise en pied.
     */
    val isOffline: Boolean = false,
    /**
     * Une ouverture d'article a été refusée faute de réseau (SPECS.md §5.2).
     *
     * Un booléen plutôt qu'un type de message : c'est le seul avis transitoire
     * de cet écran, et une abstraction arrive avec son deuxième cas d'usage
     * (AGENTS.md §2).
     */
    val isOfflineOpenNoticeVisible: Boolean = false,
    /**
     * Le serveur n'a plus répondu depuis assez longtemps pour qu'on le dise
     * (SPECS.md §4.6).
     *
     * Décidé par le domaine et non ici : ce champ ne fait que rapporter le
     * verdict. Ce que l'écran y ajoute — être hors ligne, rafraîchir déjà,
     * n'avoir rien à montrer — relève de [showsStaleNotice].
     */
    val isStaleNoticeAvailable: Boolean = false,
) {
    /**
     * Le bandeau ne s'affiche qu'**au-dessus de quelque chose à lire**.
     *
     * Sans article, l'absence de réseau n'est plus un régime dégradé mais la
     * seule chose à dire : c'est alors le message plein cadre qui l'explique,
     * et le doubler d'un bandeau reviendrait à écrire deux fois la même panne.
     */
    val showsOfflineBanner: Boolean
        get() = isOffline && articles.isNotEmpty()

    /**
     * L'invitation à rafraîchir est-elle à l'écran ?
     *
     * Trois retenues, et chacune évite un message qui aurait tort :
     *
     * - **hors ligne**, le bandeau dit déjà pourquoi le flux est ancien, et
     *   proposer « Rafraîchir » ouvrirait une porte qui ne mène nulle part.
     *   C'est aussi ce qui garantit qu'une seule bandelette occupe le bas de
     *   l'écran : l'avis d'ouverture refusée n'existe que hors ligne ;
     * - **pendant un rafraîchissement**, la demande est déjà partie ;
     * - **sans article**, il n'y a pas de flux ancien à l'écran, mais un écran
     *   vide, qui a son propre message.
     */
    val showsStaleNotice: Boolean
        get() = isStaleNoticeAvailable && !isOffline && !isRefreshing && articles.isNotEmpty()
}

/**
 * Où en est le flux.
 *
 * Les quatre issues d'un chargement — il continue, il travaille, il s'est
 * terminé, il a échoué — sont des cas distincts et non des booléens croisés :
 * SPECS.md §4.4 demande qu'une liste qui cesse de s'allonger ne soit jamais
 * confondue avec une panne, et deux drapeaux indépendants autoriseraient
 * justement l'état ambigu « ni en cours, ni fini, ni en erreur ».
 */
sealed interface DiscoverPhase {
    /** Première page en cours : rien n'est encore affichable. */
    data object InitialLoading : DiscoverPhase

    /** Des articles sont affichés, et une page reste à demander. */
    data object Idle : DiscoverPhase

    /** Page suivante en cours, sous les articles déjà affichés. */
    data object LoadingMore : DiscoverPhase

    /** Plus aucun article : le flux se termine par un message explicite. */
    data object EndOfFeed : DiscoverPhase

    /** Un chargement a échoué ; [failure] porte le message et l'action de reprise. */
    data class Failed(val failure: DiscoverFailure) : DiscoverPhase

    /**
     * Le serveur a refusé le jeton.
     *
     * Ce n'est pas une erreur de lecture mais une fin de session : le dépôt
     * l'accompagne d'une invalidation et l'aiguillage racine ramène de lui-même
     * à l'écran de connexion (SPECS.md §3.4). L'écran n'affiche donc **rien**
     * de particulier — il est sur le point de disparaître — mais il cesse de
     * demander des pages, faute de quoi le défilement en réclamerait une à
     * chaque image jusqu'à la bascule.
     */
    data object SessionEnded : DiscoverPhase
}

/**
 * Ce qui a empêché le chargement, réduit à ce qui se dit à l'utilisateur.
 *
 * `FeedError.SessionExpired` n'y figure pas : il n'appelle aucun message, et
 * l'inclure obligerait l'écran à traiter un cas qu'il ne doit pas afficher.
 * `FeedError.Unexpected` y perd son message technique — il va aux journaux,
 * jamais à l'affichage.
 */
enum class DiscoverFailure {
    NoNetwork,
    ServerUnreachable,
    Unexpected,
}
