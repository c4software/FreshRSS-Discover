package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.toAuthError
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultAuthRepository @Inject constructor(
    private val api: FreshRssApi,
    private val sessionStore: SessionStore,
    private val network: NetworkAvailability,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {
    override fun observeSession(): Flow<AuthSession?> = sessionStore.observeSession()

    /**
     * Trois étapes, dans cet ordre, et l'ordre est raisonné.
     *
     * 1. **Reconnaître l'instance** avant d'envoyer quoi que ce soit. Une faute
     *    de frappe dans l'adresse produirait sinon un `401` que l'utilisateur
     *    imputerait à son mot de passe — et le mot de passe serait parti sur un
     *    serveur qui n'est pas le sien.
     * 2. **Ouvrir la session.** `ClientLogin` n'exige aucun en-tête
     *    d'autorisation : un `401` ici désigne réellement les identifiants.
     * 3. **Vérifier que l'en-tête sera transmis**, seulement une fois le jeton
     *    obtenu. C'est le seul moment où l'échec est constatable sans être
     *    confondu avec un refus d'identifiants — et le faire *avant*
     *    d'enregistrer évite de conserver une session qui échouerait à chaque
     *    appel suivant.
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

            !forwarding.value -> AuthResult.Failure(AuthError.AuthorizationHeaderNotForwarded)

            else -> {
                val session = AuthSession(server = address, username = username, token = token)
                sessionStore.save(session)
                AuthResult.Success(session)
            }
        }
    }

    override fun observeLastSignInHint(): Flow<SignInHint?> = sessionStore.observeLastSignInHint()

    override suspend fun invalidateSession() = withContext(ioDispatcher) {
        sessionStore.invalidateTokens()
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        sessionStore.clear()
    }

    /**
     * La connectivité n'est lue qu'ici, au moment de l'échec : la constater
     * d'avance donnerait une réponse périmée — le réseau peut disparaître
     * pendant la requête, ce qui est exactement le cas à diagnostiquer.
     */
    private fun failure(outcome: ApiOutcome<*>): AuthResult<Nothing> =
        AuthResult.Failure(outcome.toAuthError(isOnline = network.isOnline()))
}
