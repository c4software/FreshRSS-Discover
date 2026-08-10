package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticlePage
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.presentation.discover.ArticleUiModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverFailure
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase

/**
 * Ce que les deux modes du flux affichent (SPECS.md §4.8).
 *
 * Un seul état pour la Liste et le Balayage, et c'est le point : SPECS.md §4.8
 * dit que le contenu et les règles de chargement sont **les mêmes**, seule la
 * présentation change. Deux états jumeaux ont existé, avec leurs transitions
 * copiées — et ils ont divergé exactement comme ARCHITECTURE.md §9.6 le
 * prédit : le rechargement du Balayage projetait des extraits à la longueur de
 * la Liste. Ce que les deux modes gardent en propre — la longueur d'extrait, la
 * page de fin du pagineur — passe par la projection et par des dérivations,
 * jamais par un second état.
 *
 * Les articles et l'état du chargement sont **séparés** : SPECS.md §4.4 exige
 * qu'un échec de page suivante ne vide pas ce qui est déjà affiché, ce qui
 * serait impossible si la liste ne vivait que dans le cas « chargé » d'un type
 * scellé.
 */
data class FeedUiState(
    val articles: List<ArticleUiModel> = emptyList(),
    val phase: DiscoverPhase = DiscoverPhase.InitialLoading,
    /**
     * Rechargement demandé par l'utilisateur (SPECS.md §4.6) — le tirage en
     * Liste, le bouton en Balayage.
     *
     * Hors de [phase], et c'est délibéré : le rechargement se fait
     * **par-dessus** un flux qui a déjà son état — au repos, terminé, en échec
     * — et le fondre dans la phase obligerait à mémoriser celle à laquelle
     * revenir.
     */
    val isRefreshing: Boolean = false,
    /**
     * La dernière requête a échoué faute de réseau (SPECS.md §5.2).
     *
     * Distinct de `DiscoverPhase.Failed(NoNetwork)`, qui dit qu'un *chargement*
     * a échoué : celui-ci dit dans quel **régime** est l'application, et c'est
     * lui qui décide du bandeau.
     */
    val isOffline: Boolean = false,
    /**
     * Une ouverture d'article a été refusée faute de réseau (SPECS.md §5.2).
     *
     * Un booléen plutôt qu'un type de message : c'est le seul avis transitoire
     * de ces écrans, et une abstraction arrive avec son deuxième cas d'usage
     * (AGENTS.md §2).
     */
    val isOfflineOpenNoticeVisible: Boolean = false,
    /**
     * Le serveur n'a plus répondu depuis assez longtemps pour qu'on le dise
     * (SPECS.md §4.6). Décidé par le domaine ; ce champ rapporte le verdict.
     */
    val isStaleNoticeAvailable: Boolean = false,
) {
    /**
     * Le bandeau hors ligne ne s'affiche qu'**au-dessus de quelque chose à
     * lire** : sans article, l'absence de réseau n'est plus un régime dégradé
     * mais la seule chose à dire, et c'est alors le message plein cadre qui
     * l'explique.
     */
    val showsOfflineBanner: Boolean
        get() = isOffline && articles.isNotEmpty()

    /**
     * L'invitation à rafraîchir est-elle à l'écran ?
     *
     * Trois retenues, et chacune évite un message qui aurait tort : hors
     * ligne, le bandeau dit déjà pourquoi le flux est ancien ; pendant un
     * rafraîchissement, la demande est déjà partie ; sans article, il n'y a
     * pas de flux ancien mais un écran vide, qui a son propre message.
     */
    val showsStaleNotice: Boolean
        get() = isStaleNoticeAvailable && !isOffline && !isRefreshing && articles.isNotEmpty()
}

/**
 * Projette un article du domaine dans sa forme affichable.
 *
 * C'est **la seule** chose qui distingue les deux modes dans les transitions
 * ci-dessous : la longueur de l'extrait (SPECS.md §8, question 7). La passer en
 * paramètre est ce qui permet d'écrire ces transitions une fois.
 */
typealias ArticleProjection = (Article, Long) -> ArticleUiModel

/**
 * Remplace la liste par la page rendue, et repart du début (SPECS.md §4.6).
 *
 * Rien n'est remis à zéro côté ViewModel, et il faut le dire parce que ce
 * serait le réflexe : ni le détecteur de lecture, dont `onVisibilityChanged`
 * écarte de lui-même les chronomètres des articles absents ; ni les articles
 * déjà signalés au serveur — les resignaler ferait une requête pour rien.
 *
 * La phase suit la page rendue : `Idle` s'il reste un curseur, `EndOfFeed`
 * sinon. Elle lève donc aussi l'échec précédent — le réseau vient de répondre —
 * et rouvre un flux qui s'était terminé si le serveur a du neuf.
 */
internal fun FeedUiState.refreshedWith(
    page: ArticlePage,
    nowEpochMillis: Long,
    project: ArticleProjection,
): FeedUiState = copy(
    articles = page.articles.map { article -> project(article, nowEpochMillis) },
    phase = if (page.hasMore) DiscoverPhase.Idle else DiscoverPhase.EndOfFeed,
    isOffline = false,
)

/**
 * Ajoute les articles absents de la liste, sans toucher à ceux qui y sont.
 *
 * Ce qui est déjà affiché n'est jamais réordonné : la règle 3 de SPECS.md §4.2
 * veut qu'un même ensemble d'articles se présente toujours dans le même ordre.
 * En Balayage l'enjeu est plus aigu encore — réordonner sous le doigt
 * changerait l'article que le geste suivant va montrer.
 *
 * @param atHead vrai pour insérer les inconnus **en tête**, ce que fait la
 *   première page du serveur par-dessus le cache déjà affiché.
 */
internal fun FeedUiState.merging(
    articles: List<Article>,
    nowEpochMillis: Long,
    atHead: Boolean,
    project: ArticleProjection,
): FeedUiState {
    val known = this.articles.mapTo(mutableSetOf(), ArticleUiModel::id)
    val fresh = articles.filterNot { it.id.value in known }.map { project(it, nowEpochMillis) }
    if (fresh.isEmpty()) return this

    return copy(articles = if (atHead) fresh + this.articles else this.articles + fresh)
}

/**
 * Le drapeau change, l'article ne bouge pas : le retirer déplacerait la
 * lecture. **Et il ne repasse jamais à faux** (GOAL-012-T04) : il n'existe pas
 * de transition inverse.
 */
internal fun FeedUiState.markingRead(ids: Set<ArticleId>): FeedUiState =
    copy(articles = articles.map { if (ArticleId(it.id) in ids) it.copy(isRead = true) else it })

/**
 * Les articles déjà chargés sont **conservés** (SPECS.md §4.4) : les effacer
 * parce que la page suivante a échoué punirait l'utilisateur de s'être approché
 * du bas du flux. Hors ligne, ils sont même l'essentiel de ce qui reste.
 */
internal fun FeedUiState.failedWith(error: FeedError): FeedUiState = copy(
    phase = when (error) {
        FeedError.SessionExpired -> DiscoverPhase.SessionEnded
        FeedError.NoNetwork -> DiscoverPhase.Failed(DiscoverFailure.NoNetwork)
        FeedError.ServerUnreachable -> DiscoverPhase.Failed(DiscoverFailure.ServerUnreachable)
        is FeedError.Unexpected -> DiscoverPhase.Failed(DiscoverFailure.Unexpected)
    },
    isOffline = error == FeedError.NoNetwork,
)

/**
 * Cache garni au lancement : rien à demander, le flux est celui qu'on a laissé
 * (SPECS.md §5.1). La phase passe au repos — sans elle, l'écran annoncerait un
 * chargement qui n'existe pas.
 */
internal fun FeedUiState.settledFromCache(): FeedUiState =
    if (phase == DiscoverPhase.InitialLoading) copy(phase = DiscoverPhase.Idle) else this
