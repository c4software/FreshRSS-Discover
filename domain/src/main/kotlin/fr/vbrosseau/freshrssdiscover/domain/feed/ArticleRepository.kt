package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlinx.coroutines.flow.Flow

/**
 * Nombre d'articles à demander à [ArticleRepository.observeCachedArticles].
 *
 * Cinq pages : de quoi retrouver plusieurs écrans de défilement au lancement,
 * sans lire tout le cache pour n'en afficher que le haut. La borne est
 * indispensable — le cache n'est purgé que de ses articles lus (SPECS.md §5.3),
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
 * **Ce que le dépôt tient, et ce qu'il ne tient pas.** Il rend des pages déjà
 * mélangées et n'accumule rien : la liste affichée appartient à l'appelant.
 * Deux raisons — l'appelant est le seul à savoir ce qui est réellement à
 * l'écran (SPECS.md §4.6 demande de préserver la position de lecture), et un
 * dépôt porteur de la liste la rendrait indisponible aux tests de l'écran sans
 * passer par lui. Le dépôt ne conserve donc qu'une chose : la fin de la
 * dernière page paginée, sans laquelle la règle 4 de SPECS.md §4.2 — pas de
 * monotonie de source à la jonction entre deux pages — ne pourrait pas tenir.
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
     *   (docs/freshrss-api.md §3.5). `null` repart aussi de zéro pour le
     *   mélange : rien ne précède la première page.
     */
    suspend fun loadPage(cursor: PageCursor? = null): FeedResult<ArticlePage>

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
     * N'affecte pas la continuité de la pagination : le curseur et la fin de
     * page que le dépôt conserve désignent le **bas** du flux affiché, que le
     * rafraîchissement ne touche pas. Un `loadPage(cursor)` suivant reprend
     * donc exactement où il en était.
     */
    suspend fun refresh(): FeedResult<ArticlePage>
}
