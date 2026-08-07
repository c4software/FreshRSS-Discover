package fr.vbrosseau.freshrssdiscover.domain.feed

/**
 * Mémorise où l'utilisateur en était dans le flux.
 *
 * C'est la contrepartie du tirer-pour-rafraîchir (SPECS.md §4.6), qui remonte
 * délibérément en haut : une fermeture, elle, n'est pas une demande de
 * l'utilisateur, et lui reprendre sa place à chaque retour rendrait un flux
 * continu et sans repère impraticable.
 */
interface ReadingPositionRepository {
    /** Dernière position mémorisée, `null` si aucune. */
    suspend fun lastPosition(): ReadingPosition?

    /** Retient [position] comme point de reprise. */
    suspend fun remember(position: ReadingPosition)

    /** Oublie la position. Appelé à la déconnexion, avec le reste. */
    suspend fun forget()
}
