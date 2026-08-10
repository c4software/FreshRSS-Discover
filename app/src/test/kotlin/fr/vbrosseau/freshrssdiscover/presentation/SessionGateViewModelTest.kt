package fr.vbrosseau.freshrssdiscover.presentation

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.FakeAuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.auth.SignInHint
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SessionGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val sessions = MutableStateFlow<AuthSession?>(null)
    private val repository = FakeAuthRepository(sessions)

    @Test
    fun theGateStartsUnknownWhileTheSessionIsStillBeingRead() = runTest {
        // The session lives on disk: starting from "signed out" would flash
        // the login screen at every launch, even for a signed-in user.
        //
        // The source must not emit immediately: a `MutableStateFlow` already
        // has a value and would mask the defect.
        val viewModel = SessionGateViewModel(SilentAuthRepository)

        assertEquals(SessionGate.Unknown, viewModel.gate.value)
    }

    /** Repository whose session is never read, like a DataStore still silent. */
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
        // The whole mechanism of SPECS.md §3.4: no screen has to care, the
        // disappearance of the session is enough.
        sessions.value = repository.sessionOf(server)
        val viewModel = SessionGateViewModel(repository)
        assertEquals(SessionGate.SignedIn, viewModel.gate.value)

        repository.invalidateSession()

        assertEquals(SessionGate.SignedOut, viewModel.gate.value)
    }
}
