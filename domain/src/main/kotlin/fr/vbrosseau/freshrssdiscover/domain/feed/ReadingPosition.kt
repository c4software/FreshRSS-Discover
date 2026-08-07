package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Où la lecture s'était arrêtée (SPECS.md §5.3).
 *
 * **La date accompagne l'identifiant, et ce n'est pas un confort.** L'article
 * retenu est celui en tête d'écran — c'est-à-dire précisément celui que le
 * marquage automatique vient de rendre lu, et que le flux des non-lus exclut
 * donc à la réouverture. Chercher l'identifiant exact échouerait presque
 * toujours, et la reprise se ferait invariablement en haut : la fonctionnalité
 * serait vraie sur le papier et inopérante en pratique.
 *
 * Constaté sur un flux réel : après quatre écrans de défilement, l'article
 * mémorisé avait disparu des quarante premiers non-lus.
 *
 * La date permet la reprise « au plus proche » que SPECS.md §5.3 demande : on
 * se place au premier article qui n'est pas **plus récent** que celui qu'on
 * regardait. Le flux étant globalement antichronologique (§4.2), c'est
 * l'article qui suivait immédiatement.
 */
data class ReadingPosition(
    val articleId: ArticleId,
    /** Date de publication de l'article, en secondes depuis l'époque Unix. */
    val publishedAtEpochSeconds: Long,
) {
    /**
     * Rang auquel reprendre, ou `null` si rien ne convient.
     *
     * Trois cas, dans cet ordre : l'article est encore là ; il a disparu, et on
     * prend le premier qui n'est **pas plus récent** ; le flux ne contient que
     * des articles plus récents — tout est nouveau depuis, et le haut est alors
     * la bonne place.
     *
     * Prend des [Candidate] et non des `Article` : l'appelant est la couche
     * présentation, qui manipule sa propre projection. Réécrire la règle
     * là-haut la ferait diverger de celle-ci.
     */
    fun indexIn(candidates: List<Candidate>): Int? {
        val exact = candidates.indexOfFirst { it.id == articleId.value }
        if (exact >= 0) return exact

        return candidates
            .indexOfFirst { it.publishedAtEpochSeconds <= publishedAtEpochSeconds }
            .takeIf { it >= 0 }
    }

    /** Le minimum nécessaire pour situer un article dans le flux affiché. */
    data class Candidate(
        val id: Long,
        val publishedAtEpochSeconds: Long,
    )
}
