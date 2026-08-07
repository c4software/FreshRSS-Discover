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
) {
    /**
     * Vrai quand le flux est arrivé au bout **sans avoir rien à montrer**.
     *
     * C'est un cas distinct de la fin de flux ordinaire : « vous avez tout lu »
     * sous une liste vide n'explique rien.
     */
    val isEmptyFeed: Boolean
        get() = articles.isEmpty() && phase == DiscoverPhase.EndOfFeed
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
