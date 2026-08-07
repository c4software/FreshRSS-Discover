package fr.vbrosseau.freshrssdiscover.presentation

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthResult
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.FakeAuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.auth.SignInHint
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SessionGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val sessions = MutableStateFlow<AuthSession?>(null)
    private val repository = FakeAuthRepository(sessions)

    @Test
    fun theGateStartsUnknownWhileTheSessionIsStillBeingRead() = runTest {
        // La session vit sur disque : partir de « déconnecté » ferait
        // apparaître l'écran de connexion un instant à chaque lancement, y
        // compris pour un utilisateur déjà connecté.
        //
        // La source ne doit donc **pas** émettre tout de suite — un
        // `MutableStateFlow` a déjà une valeur et masquerait le défaut.
        val viewModel = SessionGateViewModel(SilentAuthRepository)

        assertEquals(SessionGate.Unknown, viewModel.gate.value)
    }

    /** Dépôt dont la session n'est jamais lue, comme un DataStore encore muet. */
    private object SilentAuthRepository : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = emptyFlow()
        override fun observeLastSignInHint(): Flow<SignInHint?> = emptyFlow()
        override suspend fun signIn(address: ServerAddress, credentials: Credentials) =
            Outcome.Failure(AuthError.NoNetwork)
        override suspend fun invalidateSession() = Unit
        override suspend fun signOut() = Unit
    }

    @Test
    fun anAbsentSessionLeadsToTheLoginScreen() = runTest {
        val viewModel = SessionGateViewModel(repository)

        assertEquals(SessionGate.SignedOut, viewModel.gate.value)
    }

    @Test
    fun aPresentSessionOpensTheApplication() = runTest {
        sessions.value = repository.sessionOf(server)

        val viewModel = SessionGateViewModel(repository)

        assertEquals(SessionGate.SignedIn, viewModel.gate.value)
    }

    @Test
    fun aRefusedTokenSendsTheUserBackToTheLoginScreen() = runTest {
        // C'est tout le mécanisme de SPECS.md §3.4 : aucun écran n'a à s'en
        // préoccuper, la disparition de la session suffit.
        sessions.value = repository.sessionOf(server)
        val viewModel = SessionGateViewModel(repository)
        assertEquals(SessionGate.SignedIn, viewModel.gate.value)

        repository.invalidateSession()

        assertEquals(SessionGate.SignedOut, viewModel.gate.value)
    }
}
