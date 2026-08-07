package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Mémorise où l'utilisateur en était dans le flux.
 *
 * Ce qui est retenu est l'**article** en tête d'écran, jamais son rang : le flux
 * s'allonge entre deux ouvertures, et un rang ne désignerait plus le même
 * contenu (SPECS.md §5.3).
 *
 * C'est la contrepartie du tirer-pour-rafraîchir (§4.6), qui remonte
 * délibérément en haut : une fermeture, elle, n'est pas une demande de
 * l'utilisateur, et lui reprendre sa place à chaque retour rendrait un flux
 * continu et sans repère impraticable.
 */
interface ReadingPositionRepository {
    /** Dernier article en tête d'écran, `null` si aucun n'a été mémorisé. */
    suspend fun lastPosition(): ArticleId?

    /** Retient [articleId] comme position courante. */
    suspend fun remember(articleId: ArticleId)

    /** Oublie la position. Appelé à la déconnexion, avec le reste. */
    suspend fun forget()
}
