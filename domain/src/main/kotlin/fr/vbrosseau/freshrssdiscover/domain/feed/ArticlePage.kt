package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Position dans le flux, opaque au domaine.
 *
 * C'est un **curseur relatif**, pas un rang : il désigne le dernier article
 * déjà transmis, et le serveur reprend juste après (docs/freshrss-api.md §3.5).
 * Le domaine n'a pas à le savoir — mais il ne doit pas non plus le fabriquer,
 * d'où un type dédié plutôt qu'une `String` nue.
 */
@JvmInline
value class PageCursor(val value: String)

/**
 * Une page du flux.
 *
 * [nextCursor] à `null` signifie **fin du flux**, et c'est le seul signal
 * disponible : l'API ne renvoie aucun compteur total (docs/freshrss-api.md
 * §3.5). Une page pleine sans curseur est donc une fin légitime, pas une
 * anomalie.
 */
data class ArticlePage(
    val articles: List<Article>,
    val nextCursor: PageCursor?,
) {
    /**
     * Vrai lorsqu'il reste des articles à demander.
     *
     * Nommé plutôt que laissé à un `!= null` disséminé : SPECS.md §4.4 demande
     * de distinguer « fin du flux » d'un chargement qui s'arrête, et la
     * confusion produirait une liste qui cesse simplement de s'allonger —
     * indistinguable d'une panne.
     */
    val hasMore: Boolean get() = nextCursor != null
}
