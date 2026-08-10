package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * Access to the user's session.
 *
 * Declared here, implemented in `:app`: this lets the domain express what it
 * needs without knowing anything about HTTP or storage (ARCHITECTURE.md §2).
 */
interface AuthRepository {
    /**
     * Current session, `null` when the user is not signed in.
     *
     * A flow rather than a one-shot read: it is what brings the user back to
     * the sign-in screen when the server rejects the token, without the screen
     * having to poll anything (SPECS.md §3.4).
     */
    fun observeSession(): Flow<AuthSession?>

    /**
     * Opens a session and persists it.
     *
     * The API password does not leave this call: it is never stored.
     */
    suspend fun signIn(
        address: ServerAddress,
        credentials: Credentials,
    ): AuthResult<AuthSession>

    /**
     * Last address and username used, even without a session.
     *
     * They survive a rejected token: SPECS.md §3.4 requires returning the user
     * to the sign-in screen without retyping everything, when they likely only
     * have an API password to renew.
     */
    fun observeLastSignInHint(): Flow<SignInHint?>

    /**
     * The server rejected the token: the session is dropped, the sign-in hint
     * remains.
     *
     * Distinct from [signOut], which is a deliberate user action and has no
     * reason to leave a trace.
     */
    suspend fun invalidateSession()

    /** Clears the session and the sign-in hint. Deliberate user action. */
    suspend fun signOut()
}
