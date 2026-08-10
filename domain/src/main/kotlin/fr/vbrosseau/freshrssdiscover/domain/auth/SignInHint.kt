package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Data used to prefill the sign-in screen.
 *
 * Contains no secret: this is what allows keeping it after a rejected token,
 * while the token itself is erased.
 */
data class SignInHint(
    val server: ServerAddress,
    val username: String,
)
