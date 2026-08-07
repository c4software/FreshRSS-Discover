package fr.vbrosseau.freshrssdiscover.domain.auth

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome

/**
 * Issue d'une opération d'authentification.
 *
 * Alias plutôt que type propre : la forme est celle de [Outcome], seule
 * l'erreur est spécifique. Le nom reste parce qu'il se lit mieux dans une
 * signature que `Outcome<AuthSession, AuthError>`.
 */
typealias AuthResult<T> = Outcome<T, AuthError>
