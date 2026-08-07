package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Identifiants saisis par l'utilisateur.
 *
 * [apiPassword] est le **mot de passe API** de FreshRSS, distinct du mot de
 * passe de connexion (SPECS.md §3.1). Les confondre est la première cause
 * d'échec de connexion.
 *
 * `toString` est redéfini pour masquer le secret. Ce n'est pas de la prudence
 * excessive : une `data class` produirait un `toString` complet, qu'un simple
 * `Timber.d("credentials=%s", …)` — ou le message d'une exception — suffirait à
 * écrire dans les journaux du terminal.
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
