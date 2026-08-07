package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Identifiant d'un article, sous sa forme **décimale**.
 *
 * L'API expose le même entier sous deux bases : hexadécimal dans `items[].id`,
 * décimal dans `continuation` et dans le paramètre `i` d'`edit-tag`
 * (docs/freshrss-api.md §3.4). La conversion appartient à la couche `data` ; le
 * domaine n'en connaît qu'une seule forme, sans quoi la confusion se
 * propagerait jusqu'au marquage comme lu — où elle échouerait en silence.
 */
@JvmInline
value class ArticleId(val value: Long)

/**
 * Flux d'origine d'un article.
 *
 * Le titre voyage avec l'article plutôt que d'être résolu à l'affichage : dans
 * un flux mélangé, la source est ce qui rend l'article intelligible (SPECS.md
 * §4.3), et une résolution différée la ferait apparaître après coup.
 */
data class FeedRef(
    /** Identifiant de flux tel que l'API le désigne, par exemple `feed/12`. */
    val id: String,
    val title: String,
)

/**
 * Un article du flux Discover.
 *
 * Ne contient que ce que SPECS.md §4.3 demande d'afficher. Le contenu intégral
 * n'y figure pas : l'application ouvre l'article d'origine dans le navigateur
 * (§4.7), et conserver le corps entier de chaque article gonflerait le cache
 * sans servir.
 */
data class Article(
    val id: ArticleId,
    val title: String,
    /**
     * Lien vers l'article d'origine, `null` s'il est inexploitable.
     *
     * Un article sans lien existe — flux mal formé, contenu purement local.
     * SPECS.md §4.7 demande alors de le rendre non cliquable plutôt que
     * d'ouvrir une page vide.
     */
    val url: String?,
    /** Date de publication, en secondes depuis l'époque Unix. */
    val publishedAtEpochSeconds: Long,
    /** Extrait, éventuellement vide. Le serveur le tronque déjà. */
    val summary: String,
    /** Illustration, `null` si l'article n'en a pas. Aucune image de remplacement. */
    val imageUrl: String?,
    val author: String?,
    val feed: FeedRef,
    val isRead: Boolean,
)
