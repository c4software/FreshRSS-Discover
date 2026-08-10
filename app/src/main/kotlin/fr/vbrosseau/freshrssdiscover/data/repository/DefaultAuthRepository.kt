package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.toAuthError
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.di.IoDispatcher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthResult
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.SignInHint
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultAuthRepository @Inject constructor(
    private val api: FreshRssApi,
    private val sessionStore: SessionStore,
    private val articleCache: ArticleCache,
    private val readSyncRepository: ReadSyncRepository,
    private val network: NetworkAvailability,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {
    override fun observeSession(): Flow<AuthSession?> = sessionStore.observeSession()

    /**
     * Three steps, in a deliberate order.
     *
     * 1. Recognize the instance before sending anything. A typo in the address
     *    would otherwise produce a `401` the user would blame on their
     *    password — and the password would have been sent to a server that is
     *    not theirs.
     * 2. Open the session. `ClientLogin` requires no authorization header: a
     *    `401` here genuinely designates the credentials.
     * 3. Verify that the header will be forwarded, only once the token is
     *    obtained. This is the only moment the failure can be observed without
     *    being confused with a credentials rejection — and doing it before
     *    saving avoids keeping a session that would fail every subsequent
     *    call.
     */
    override suspend fun signIn(
        address: ServerAddress,
        credentials: Credentials,
    ): AuthResult<AuthSession> = withContext(ioDispatcher) {
        val recognized = api.probe(address)
        if (recognized !is ApiOutcome.Success) {
            return@withContext failure(recognized)
        }

        when (val login = api.clientLogin(address, credentials)) {
            is ApiOutcome.Success -> completeSignIn(address, credentials.username, login.value)
            else -> failure(login)
        }
    }

    private suspend fun completeSignIn(
        address: ServerAddress,
        username: String,
        token: AuthToken,
    ): AuthResult<AuthSession> {
        val forwarding = api.checkAuthorizationForwarding(address)
        return when {
            forwarding !is ApiOutcome.Success -> failure(forwarding)

            !forwarding.value -> Outcome.Failure(AuthError.AuthorizationHeaderNotForwarded)

            else -> {
                val session = AuthSession(server = address, username = username, token = token)
                sessionStore.save(session)
                Outcome.Success(session)
            }
        }
    }

    override fun observeLastSignInHint(): Flow<SignInHint?> = sessionStore.observeLastSignInHint()

    override suspend fun invalidateSession() = withContext(ioDispatcher) {
        sessionStore.invalidateTokens()
    }

    /**
     * Wipes the session, the cache, the pending marks, and the reading
     * position.
     *
     * SPECS.md §3.5: logout is destructive by design. Leaving the articles
     * behind would expose what the user was reading to the next account
     * signed in on the device.
     *
     * The mark queue goes with them, and this is the only case where it is
     * emptied without server confirmation: these marks designate articles
     * that no longer exist locally, and transmitting them after reconnecting
     * on a different account would be worse than losing them.
     */
    override suspend fun signOut() = withContext(ioDispatcher) {
        sessionStore.clear()
        articleCache.clear()
        readSyncRepository.clearPending()
    }

    /**
     * Connectivity is only read here, at the moment of failure: checking it
     * beforehand would give a stale answer — the network can vanish during
     * the request, which is exactly the case to diagnose.
     */
    private fun failure(outcome: ApiOutcome<*>): AuthResult<Nothing> =
        Outcome.Failure(outcome.toAuthError(isOnline = network.isOnline()))
}
