package fr.vbrosseau.freshrssdiscover.domain.auth

/**
 * Issue d'une opération d'authentification.
 *
 * `kotlin.Result` n'est pas employé : il transporte un `Throwable`, ce qui
 * ferait remonter des exceptions techniques au-dessus de la couche `data`
 * (ARCHITECTURE.md §7) et laisserait l'appelant libre de ne traiter aucun cas.
 * Un type scellé, lui, se consomme par un `when` exhaustif.
 */
sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>

    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

/** Valeur si l'opération a réussi, `null` sinon. */
fun <T> AuthResult<T>.valueOrNull(): T? =
    when (this) {
        is AuthResult.Success -> value
        is AuthResult.Failure -> null
    }

/** Erreur si l'opération a échoué, `null` sinon. */
fun AuthResult<*>.errorOrNull(): AuthError? =
    when (this) {
        is AuthResult.Success -> null
        is AuthResult.Failure -> error
    }
