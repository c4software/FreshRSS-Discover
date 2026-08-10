package fr.vbrosseau.freshrssdiscover.domain.auth

import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scriptable authentication repository for tests.
 *
 * [pendingSignIn] suspends an in-flight sign-in: without it, the intermediate
 * state the user sees during the network call (disabled button, progress
 * indicator) could not be observed.
 */
class FakeAuthRepository(
    private val session: MutableStateFlow<AuthSession?> = MutableStateFlow(null),
) : AuthRepository {
    /** Result returned by the next `signIn` when no pending wait is armed. */
    var nextResult: AuthResult<AuthSession> = Outcome.Failure(AuthError.InvalidCredentials)

    /** Arms a sign-in that only completes once [completeSignIn] is called. */
    var pendingSignIn: CompletableDeferred<AuthResult<AuthSession>>? = null

    var signInCallCount: Int = 0
        private set

    var lastCredentials: Credentials? = null
        private set

    var lastAddress: ServerAddress? = null
        private set

    var signOutCallCount: Int = 0
        private set

    var invalidateCallCount: Int = 0
        private set

    /** Sign-in hint returned by `observeLastSignInHint`. */
    val hint: MutableStateFlow<SignInHint?> = MutableStateFlow(null)

    override fun observeSession(): StateFlow<AuthSession?> = session

    override suspend fun signIn(
        address: ServerAddress,
        credentials: Credentials,
    ): AuthResult<AuthSession> {
        signInCallCount++
        lastAddress = address
        lastCredentials = credentials

        val result = pendingSignIn?.await() ?: nextResult
        if (result is Outcome.Success) {
            session.value = result.value
        }
        return result
    }

    override fun observeLastSignInHint(): StateFlow<SignInHint?> = hint

    override suspend fun invalidateSession() {
        invalidateCallCount++
        session.value = null
    }

    override suspend fun signOut() {
        signOutCallCount++
        session.value = null
        hint.value = null
    }

    /** Completes the sign-in armed by [pendingSignIn]. */
    fun completeSignIn(result: AuthResult<AuthSession>) {
        checkNotNull(pendingSignIn) { "aucune connexion en attente" }.complete(result)
    }

    fun sessionOf(
        address: ServerAddress,
        username: String = "alice",
    ): AuthSession = AuthSession(server = address, username = username, token = AuthToken("$username/jeton"))
}
