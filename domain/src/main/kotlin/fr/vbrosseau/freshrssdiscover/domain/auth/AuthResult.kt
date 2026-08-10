package fr.vbrosseau.freshrssdiscover.domain.auth

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome

/**
 * Result of an authentication operation.
 *
 * An alias rather than a dedicated type: the shape is [Outcome], only the
 * error type is specific. The name reads better in signatures than
 * `Outcome<AuthSession, AuthError>`.
 */
typealias AuthResult<T> = Outcome<T, AuthError>
