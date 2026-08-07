package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Accès aux articles du flux.
 *
 * Déclaré ici, implémenté dans `:app` : le domaine exprime ce dont il a besoin
 * sans rien connaître de HTTP ni du disque (ARCHITECTURE.md §2).
 */
interface ArticleRepository {
    /**
     * Récupère une page d'articles non lus.
     *
     * @param cursor position rendue par la page précédente. `null` demande le
     *   début du flux — et **seulement** `null` : fabriquer un curseur vide
     *   ferait recommencer la première page sans que rien ne le signale
     *   (docs/freshrss-api.md §3.5).
     */
    suspend fun loadPage(cursor: PageCursor? = null): FeedResult<ArticlePage>
}
