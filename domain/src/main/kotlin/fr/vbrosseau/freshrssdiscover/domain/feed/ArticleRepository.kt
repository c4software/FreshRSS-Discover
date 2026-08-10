package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlinx.coroutines.flow.Flow

/**
 * Nombre d'articles à demander à [ArticleRepository.observeCachedArticles].
 *
 * Cinq pages : de quoi retrouver plusieurs écrans de défilement au lancement,
 * sans lire tout le cache pour n'en afficher que le haut. La borne est
 * indispensable — le cache n'est purgé que de ses articles lus (SPECS.md §5.4),
 * un flux prolifique y accumulerait donc des milliers de lignes à charger avant
 * la première image.
 *
 * Passée explicitement plutôt que valeur par défaut du paramètre : une valeur
 * par défaut d'interface engendre une méthode de pont que rien n'exécute, et
 * qui apparaîtrait comme non couverte dans un module vérifié à 98 %.
 */
const val CACHED_FEED_LIMIT = 200

/**
 * Accès aux articles du flux.
 *
 * Déclaré ici, implémenté dans `:app` : le domaine exprime ce dont il a besoin
 * sans rien connaître de HTTP ni du disque (ARCHITECTURE.md §2).
 *
 * **Ce que le dépôt tient : rien.** Il rend des pages déjà mélangées et
 * n'accumule ni liste ni position : la liste affichée, le curseur **et la fin
 * de la page précédente** appartiennent à l'appelant. L'appelant est le seul à
 * savoir ce qui est réellement à l'écran (SPECS.md §4.6 demande de préserver la
 * position de lecture) — et le dépôt étant un singleton partagé par les deux
 * modes de présentation, y loger la fin de page faisait contaminer la jonction
 * d'un mode par la pagination de l'autre.
 */
interface ArticleRepository {
    /**
     * Récupère une page d'articles non lus, **déjà mélangée** (SPECS.md §4.2).
     *
     * L'ordre rendu est celui à afficher : l'appelant ne réordonne pas, sans
     * quoi la règle 4 tomberait — lui seul verrait la jonction entre deux
     * pages, mais lui seul ignore comment la précédente a été ordonnée.
     *
     * @param cursor position rendue par la page précédente. `null` demande le
     *   début du flux — et **seulement** `null` : fabriquer un curseur vide
     *   ferait recommencer la première page sans que rien ne le signale
     *   (docs/freshrss-api.md §3.5).
     * @param previousTail la fin de la page précédente **telle que rendue** —
     *   son dernier article suffit. C'est ce qui fait tenir la règle 4 de
     *   SPECS.md §4.2 à la jonction entre deux pages : la monotonie ne se juge
     *   qu'entre voisins immédiats. Vide pour la première page, et vide après
     *   un rechargement dont la queue est celle de la page rafraîchie — la
     *   queue suit le parcours que suit le curseur.
     */
    suspend fun loadPage(
        cursor: PageCursor? = null,
        previousTail: List<Article> = emptyList(),
    ): FeedResult<ArticlePage>

    /**
     * Les [limit] articles non lus du cache, mélangés, du plus récent au plus
     * ancien.
     *
     * Sert deux besoins d'un seul flux, et c'est délibéré :
     *
     * - **au lancement** (SPECS.md §5.1), le flux s'affiche immédiatement,
     *   avant que la moindre requête n'aboutisse ;
     * - **hors ligne** (SPECS.md §5.2), il reste consultable, la requête ayant
     *   échoué.
     *
     * C'est la réponse au fait qu'une page issue du cache n'a pas de curseur :
     * elle n'est **jamais** rendue comme une [ArticlePage]. Un `nextCursor` à
     * `null` signifie « fin du flux » et rien d'autre (voir [ArticlePage]) ;
     * habiller le cache en page ferait afficher « vous avez tout lu » à un
     * utilisateur simplement privé de réseau. Le cache est donc une **source
     * parallèle et permanente**, et l'échec réseau reste rapporté tel quel par
     * [loadPage] — à charge pour l'appelant de le signaler sans alarmer,
     * puisqu'il a du contenu à montrer.
     *
     * Le flux émet à chaque écriture du cache : une page réseau enregistrée s'y
     * répercute d'elle-même.
     */
    fun observeCachedArticles(limit: Int): Flow<List<Article>>

    /**
     * Redemande le début du flux (SPECS.md §4.6).
     *
     * Rend la **première page telle qu'elle est aujourd'hui**, mélangée entre
     * ses seuls articles : rien ne la précède. Elle contient donc aussi des
     * articles déjà affichés — l'API n'a pas de notion de « depuis la dernière
     * fois » (docs/freshrss-api.md §3.5).
     *
     * L'appelant insère **en tête** ceux qu'il ne connaît pas encore, et laisse
     * les autres à leur place : le rafraîchissement ne réordonne pas ce qui est
     * déjà affiché (règle 3 de SPECS.md §4.2). Le dédoublonnage lui revient
     * pour la même raison que l'accumulation — lui seul sait ce qui est à
     * l'écran.
     *
     * N'affecte pas la continuité de la pagination : curseur et fin de page
     * vivent chez l'appelant, et c'est lui qui décide de repartir de la page
     * rafraîchie.
     */
    suspend fun refresh(): FeedResult<ArticlePage>

    /**
     * Ce qu'il reste à lire dans le **cache**, sans toucher au réseau.
     *
     * `FeedResult` serait de trop : il n'y a pas d'échec à rapporter, un cache
     * vide se disant très bien par une liste vide. C'est la différence avec
     * [loadPage], qui peut trouver le serveur injoignable.
     *
     * L'absence de réseau n'est pas une commodité mais le contrat : l'appelant
     * est le rappel de lecture (SPECS.md §4.9), et SPECS.md §2 exclut toujours
     * la synchronisation en arrière-plan. Une implémentation qui irait chercher
     * une page ferait sortir une requête sans geste de l'utilisateur (§7.4).
     */
    suspend fun unreadFromCache(limit: Int): List<Article>
}
