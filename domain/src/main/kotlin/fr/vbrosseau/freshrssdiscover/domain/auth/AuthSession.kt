package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Open session: everything needed to talk to the server.
 *
 * [modificationToken] is null until a modifying operation has been attempted:
 * it requires a separate call, which would be wasteful on every sign-in
 * (docs/freshrss-api.md §2.3).
 *
 * The generated `toString` is safe: [AuthToken] and [ModificationToken] mask
 * their own values.
 */
data class AuthSession(
    val server: ServerAddress,
    val username: String,
    val token: AuthToken,
    val modificationToken: ModificationToken? = null,
)
