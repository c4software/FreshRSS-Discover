package fr.vbrosseau.freshrssdiscover.presentation.login

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthResult
import fr.vbrosseau.freshrssdiscover.domain.auth.FakeAuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.presentation.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = LoginViewModel(repository)

    private fun fillValidForm() {
        viewModel.onServerAddressChange("rss.exemple.org")
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("mot-de-passe-api")
    }

    // ----- Champs dérivés ----------------------------------------------------

    @Test
    fun submissionStaysDisabledUntilEveryFieldIsFilled() {
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onServerAddressChange("rss.exemple.org")
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onUsernameChange("alice")
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onApiPasswordChange("mot-de-passe-api")
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun aWhitespaceOnlyUsernameDoesNotCountAsFilled() {
        viewModel.onServerAddressChange("rss.exemple.org")
        viewModel.onUsernameChange("   ")
        viewModel.onApiPasswordChange("mot-de-passe-api")

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun aPasswordMadeOfSpacesIsAccepted() {
        // Contrairement à l'identifiant : un mot de passe peut légitimement
        // contenir des espaces, et les rejeter empêcherait de se connecter.
        viewModel.onServerAddressChange("rss.exemple.org")
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("   ")

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun anInsecureAddressIsFlaggedWithoutBlocking() {
        // Les instances auto-hébergées sur réseau local sont un cas réel : on
        // avertit, on n'interdit pas.
        viewModel.onServerAddressChange("http://192.168.1.20:8080")
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("x")

        assertTrue(viewModel.uiState.value.showsInsecureWarning)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun anHttpsAddressRaisesNoWarning() {
        viewModel.onServerAddressChange("rss.exemple.org")

        assertFalse(viewModel.uiState.value.showsInsecureWarning)
    }

    @Test
    fun theWarningDisappearsWhenTheAddressChangesBack() {
        viewModel.onServerAddressChange("http://exemple.org")
        assertTrue(viewModel.uiState.value.showsInsecureWarning)

        viewModel.onServerAddressChange("https://exemple.org")
        assertFalse(viewModel.uiState.value.showsInsecureWarning)
    }

    // ----- Adresse rejetée avant tout appel réseau ---------------------------

    @Test
    fun anEmptyAddressIsRejectedWithoutContactingAnyServer() {
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("x")

        viewModel.submit()

        assertEquals(LoginFailure.Address.Blank, viewModel.uiState.value.failure)
        assertEquals(0, repository.signInCallCount)
    }

    @Test
    fun anUnusableAddressIsRejectedWithoutContactingAnyServer() {
        viewModel.onServerAddressChange("ex emple.org")
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("x")

        viewModel.submit()

        assertEquals(LoginFailure.Address.Malformed, viewModel.uiState.value.failure)
        assertEquals(0, repository.signInCallCount)
    }

    @Test
    fun anUnsupportedSchemeNamesItselfSoTheMessageCanBeUseful() {
        viewModel.onServerAddressChange("ftp://exemple.org")
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("x")

        viewModel.submit()

        assertEquals(
            LoginFailure.Address.UnsupportedScheme("ftp"),
            viewModel.uiState.value.failure,
        )
    }

    // ----- Connexion ---------------------------------------------------------

    @Test
    fun theNormalizedAddressIsWhatReachesTheRepository() {
        // L'utilisateur saisit ce qu'il connaît ; le dépôt reçoit une forme
        // canonique.
        fillValidForm()
        repository.nextResult = successFor("rss.exemple.org")

        viewModel.submit()

        assertEquals("https://rss.exemple.org", repository.lastAddress?.baseUrl)
    }

    @Test
    fun theUsernameIsTrimmedButThePasswordIsNot() {
        // Une espace collée par un copier-coller ne doit pas faire échouer la
        // connexion ; en retirer une du mot de passe la ferait échouer.
        viewModel.onServerAddressChange("rss.exemple.org")
        viewModel.onUsernameChange("  alice  ")
        viewModel.onApiPasswordChange(" secret ")
        repository.nextResult = successFor("rss.exemple.org")

        viewModel.submit()

        assertEquals("alice", repository.lastCredentials?.username)
        assertEquals(" secret ", repository.lastCredentials?.apiPassword)
    }

    @Test
    fun theFormIsLockedWhileTheRequestIsInFlight() = runTest {
        // Sans cela, un double appui enverrait deux connexions.
        fillValidForm()
        repository.pendingSignIn = CompletableDeferred()

        viewModel.submit()

        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.submit()
        assertEquals(1, repository.signInCallCount)

        repository.completeSignIn(successFor("rss.exemple.org"))
    }

    @Test
    fun thePasswordLeavesTheStateOnceItHasServed() = runTest {
        // Un UiState survit à l'écran qui l'affiche : il se retrouverait dans
        // un instantané de débogage ou une restauration de processus.
        fillValidForm()
        repository.nextResult = successFor("rss.exemple.org")

        viewModel.submit()

        assertEquals("", viewModel.uiState.value.apiPassword)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun aServerFailureIsSurfacedWithItsCause() {
        fillValidForm()
        repository.nextResult = AuthResult.Failure(AuthError.ApiDisabled)

        viewModel.submit()

        val failure = assertIs<LoginFailure.Server>(viewModel.uiState.value.failure)
        assertEquals(AuthError.ApiDisabled, failure.error)
    }

    @Test
    fun aFailedAttemptKeepsWhatTheUserTyped() {
        // Retaper l'adresse et l'identifiant après chaque échec serait
        // pénible, et l'erreur porte souvent sur le seul mot de passe.
        fillValidForm()
        repository.nextResult = AuthResult.Failure(AuthError.InvalidCredentials)

        viewModel.submit()

        assertEquals("rss.exemple.org", viewModel.uiState.value.serverAddress)
        assertEquals("alice", viewModel.uiState.value.username)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun dismissingTheFailureKeepsTheForm() {
        fillValidForm()
        repository.nextResult = AuthResult.Failure(AuthError.NoNetwork)
        viewModel.submit()

        viewModel.dismissFailure()

        assertNull(viewModel.uiState.value.failure)
        assertEquals("alice", viewModel.uiState.value.username)
    }

    @Test
    fun aNewAttemptClearsThePreviousFailure() {
        fillValidForm()
        repository.nextResult = AuthResult.Failure(AuthError.NoNetwork)
        viewModel.submit()

        repository.nextResult = successFor("rss.exemple.org")
        viewModel.submit()

        assertNull(viewModel.uiState.value.failure)
    }

    private fun successFor(raw: String): AuthResult.Success<fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession> {
        val address = (ServerAddress.parse(raw) as ServerAddressResult.Valid).address
        return AuthResult.Success(repository.sessionOf(address))
    }
}
