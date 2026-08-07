package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * De quoi préremplir l'écran de connexion.
 *
 * Ne contient **aucun secret** : c'est ce qui permet de le conserver après un
 * jeton refusé, là où le jeton lui-même est effacé.
 */
data class SignInHint(
    val server: ServerAddress,
    val username: String,
)
