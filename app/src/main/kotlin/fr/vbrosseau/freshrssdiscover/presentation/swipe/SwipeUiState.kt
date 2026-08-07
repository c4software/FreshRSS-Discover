package fr.vbrosseau.freshrssdiscover.presentation.swipe

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase

/**
 * Ce que la vue Balayage affiche (SPECS.md §4.8).
 *
 * Le type est propre à ce mode, mais [DiscoverPhase] et [DiscoverFailure] sont
 * **repris tels quels** : SPECS.md §4.8 dit que le contenu et les règles de
 * chargement sont les mêmes, et seule la présentation change. Dupliquer la
 * machine à états ferait exister deux vérités sur « où en est le flux », qui
 * divergeraient au premier correctif appliqué d'un seul côté.
 *
 * Le rechargement de SPECS.md §4.6 y figure, mais **pas son geste** : tirer est
 * un mouvement vertical sur une liste, et en plein écran il n'y a pas de liste
 * à tirer — le superposer au balayage horizontal donnerait deux gestes
 * concurrents sur la même surface. C'est un bouton qui le déclenche ici, et
 * l'état à porter est le même : [isRefreshing].
 *
 * Ce que cet état n'a toujours pas : de position de défilement. Le balayage la
 * tient lui-même, dans son pagineur.
 */
data class SwipeUiState(
    val articles: List<ArticleUiModel> = emptyList(),
    val phase: DiscoverPhase = DiscoverPhase.InitialLoading,
    /** Régime hors ligne (SPECS.md §5.2) : ce qui est affiché vient du cache. */
    val isOffline: Boolean = false,
    /** Une ouverture d'article a été refusée faute de réseau (SPECS.md §5.2). */
    val isOfflineOpenNoticeVisible: Boolean = false,
    /** Un rechargement demandé par l'utilisateur est en cours (SPECS.md §4.6). */
    val isRefreshing: Boolean = false,
) {
    /**
     * Nombre d'écrans que le balayage traverse : les articles, **plus un**.
     *
     * Cette page supplémentaire est la traduction du pied de liste du mode
     * Liste : c'est là que la fin du flux se dit, que le chargement se voit et
     * que l'échec propose sa reprise. Sans elle, le balayage cesserait
     * simplement de répondre après le dernier article — indistinguable d'une
     * panne (SPECS.md §4.4).
     */
    val pageCount: Int get() = articles.size + 1

    /**
     * Le bandeau hors ligne ne s'affiche qu'**au-dessus de quelque chose à
     * lire** : sans article, l'absence de réseau n'est plus un régime dégradé
     * mais la seule chose à dire, et c'est alors le message plein cadre qui
     * l'explique.
     */
    val showsOfflineBanner: Boolean get() = isOffline && articles.isNotEmpty()
}

/**
 * Ajoute les articles absents, sans toucher à ceux qui sont déjà là.
 *
 * Même règle qu'en mode Liste, et pour la même raison : la règle 3 de
 * SPECS.md §4.2 veut qu'un même ensemble d'articles se présente toujours dans
 * le même ordre. Ici l'enjeu est plus aigu encore — réordonner sous le doigt
 * changerait l'article que le balayage suivant va montrer.
 *
 * @param atHead vrai pour insérer les inconnus en tête, ce que fait la première
 *   page du serveur par-dessus le cache déjà affiché.
 */
internal fun SwipeUiState.merging(
    articles: List<Article>,
    nowEpochMillis: Long,
    atHead: Boolean,
): SwipeUiState {
    val known = this.articles.mapTo(mutableSetOf(), ArticleUiModel::id)
    val fresh = articles.filterNot { it.id.value in known }.map { it.toSwipeUiModel(nowEpochMillis) }
    if (fresh.isEmpty()) return this

    return copy(articles = if (atHead) fresh + this.articles else this.articles + fresh)
}

/**
 * Le drapeau change, l'article ne bouge pas.
 *
 * **Et il ne repasse jamais à faux** (SPECS.md §4.8, GOAL-012-T04) : revenir
 * en arrière sur un article lu ne le délie pas. C'est ce que garantit
 * l'absence de toute transition inverse — il n'existe pas de « markingUnread ».
 */
internal fun SwipeUiState.markingRead(ids: Set<ArticleId>): SwipeUiState =
    copy(articles = articles.map { if (ArticleId(it.id) in ids) it.copy(isRead = true) else it })

/**
 * Les articles déjà chargés sont **conservés** (SPECS.md §4.4) : un échec de
 * page suivante ne doit pas vider ce que l'utilisateur est en train de lire.
 */
internal fun SwipeUiState.failedWith(error: FeedError): SwipeUiState = copy(
    phase = when (error) {
        FeedError.SessionExpired -> DiscoverPhase.SessionEnded
        FeedError.NoNetwork -> DiscoverPhase.Failed(DiscoverFailure.NoNetwork)
        FeedError.ServerUnreachable -> DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable)
        is FeedError.Unexpected -> DiscoverPhase.Failed(DiscoverFailure.Unexpected)
    },
    isOffline = error == FeedError.NoNetwork,
)
