package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Dépôt d'authentification piloté, pour les tests.
 *
 * [pendingSignIn] permet de suspendre une connexion en cours : sans cela, il
 * serait impossible d'observer l'état intermédiaire — bouton désactivé,
 * indicateur de progression — que l'utilisateur voit pendant l'appel réseau.
 */
class FakeAuthRepository(
    private val session: MutableStateFlow<AuthSession?> = MutableStateFlow(null),
) : AuthRepository {
    /** Issue renvoyée par le prochain `signIn`, si aucune attente n'est armée. */
    var nextResult: AuthResult<AuthSession> = AuthResult.Failure(AuthError.InvalidCredentials)

    /** Arme une connexion qui ne se terminera qu'une fois [completeSignIn] appelée. */
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

    /** Rappel de saisie renvoyé par `observeLastSignInHint`. */
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
        if (result is AuthResult.Success) {
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

    /** Débloque la connexion armée par [pendingSignIn]. */
    fun completeSignIn(result: AuthResult<AuthSession>) {
        checkNotNull(pendingSignIn) { "aucune connexion en attente" }.complete(result)
    }

    fun sessionOf(
        address: ServerAddress,
        username: String = "alice",
    ): AuthSession = AuthSession(server = address, username = username, token = AuthToken("$username/jeton"))
}
