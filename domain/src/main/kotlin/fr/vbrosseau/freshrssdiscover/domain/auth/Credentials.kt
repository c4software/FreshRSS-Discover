package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Credentials entered by the user.
 *
 * [apiPassword] is the FreshRSS API password, distinct from the login password
 * (SPECS.md §3.1). Confusing the two is the leading cause of sign-in failures.
 *
 * `toString` is overridden to mask the secret: a `data class` would generate a
 * full `toString`, which a simple `Timber.d("credentials=%s", …)` or an
 * exception message would write to the device logs.
 */
class Credentials(
    val username: String,
    val apiPassword: String,
) {
    override fun toString(): String = "Credentials(username=$username, apiPassword=***)"

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Credentials && username == other.username && apiPassword == other.apiPassword)

    override fun hashCode(): Int = 31 * username.hashCode() + apiPassword.hashCode()
}
