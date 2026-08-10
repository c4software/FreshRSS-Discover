package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Authentication token returned by the server.
 *
 * Its shape (`<user>/<digest>`) and how it travels in a header are FreshRSS
 * API details confined to the `data` layer (ARCHITECTURE.md §2.1). The domain
 * only sees an opaque value.
 *
 * It does not expire: it is a deterministic digest of the API password
 * (docs/freshrss-api.md §2.1), so it is kept across launches. It does become
 * invalid without notice if the user changes their API password, hence
 * [AuthError.InvalidCredentials] on a request that worked the day before.
 *
 * `toString` masks the value: it is a secret, like a password.
 */
class AuthToken(val value: String) {
    override fun toString(): String = "AuthToken(***)"

    override fun equals(other: Any?): Boolean = this === other || (other is AuthToken && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}

/**
 * Token required by the API's modifying operations.
 *
 * Distinct from [AuthToken]: it can only be obtained once authenticated, and
 * it is transmitted differently. Confusing the two would produce a `401` that
 * is hard to diagnose, hence two types rather than a `String` alias.
 */
class ModificationToken(val value: String) {
    override fun toString(): String = "ModificationToken(***)"

    override fun equals(other: Any?): Boolean = this === other || (other is ModificationToken && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}
