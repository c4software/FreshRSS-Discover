package fr.vbrosseau.freshrssdiscover.presentation.login

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.domain.auth.FakeAuthRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.auth.SignInHint
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
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

    /**
     * Built lazily, and necessarily so: the ViewModel starts its prefill on
     * creation, on `Dispatchers.Main`. A property initializer runs before
     * [MainDispatcherRule] has substituted it, so the coroutine would start on
     * the real main dispatcher, absent outside Android.
     */
    private val viewModel: LoginViewModel by lazy { LoginViewModel(repository) }

    private fun fillValidForm() {
        viewModel.onServerAddressChange("rss.exemple.org")
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("mot-de-passe-api")
    }

    // ----- Derived fields ----------------------------------------------------

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
        // Unlike the username: a password may legitimately contain spaces, and
        // rejecting them would prevent signing in.
        viewModel.onServerAddressChange("rss.exemple.org")
        viewModel.onUsernameChange("alice")
        viewModel.onApiPasswordChange("   ")

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun anInsecureAddressIsFlaggedWithoutBlocking() {
        // Self-hosted instances on a local network are a real case: warn,
        // do not block.
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

    // ----- Address rejected before any network call --------------------------

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

    // ----- Prefill after a rejected token ------------------------------------

    @Test
    fun theFormIsPrefilledWithTheLastServerAndUsername() {
        // After a rejected token, the user probably only needs to renew the
        // API password: retyping the address would be pointless.
        repository.hint.value = SignInHint(
            server = (ServerAddress.parse("rss.exemple.org") as ServerAddressResult.Valid).address,
            username = "alice",
        )

        val prefilled = LoginViewModel(repository)

        assertEquals("https://rss.exemple.org", prefilled.uiState.value.serverAddress)
        assertEquals("alice", prefilled.uiState.value.username)
    }

    @Test
    fun theApiPasswordIsNeverPrefilled() {
        // It is not stored and must not be: it is the one secret the user
        // re-enters.
        repository.hint.value = SignInHint(
            server = (ServerAddress.parse("rss.exemple.org") as ServerAddressResult.Valid).address,
            username = "alice",
        )

        val prefilled = LoginViewModel(repository)

        assertEquals("", prefilled.uiState.value.apiPassword)
        assertFalse(prefilled.uiState.value.canSubmit)
    }

    @Test
    fun anAbsentHintLeavesAnEmptyForm() {
        assertEquals("", viewModel.uiState.value.serverAddress)
        assertEquals("", viewModel.uiState.value.username)
    }

    // ----- Sign-in -----------------------------------------------------------

    @Test
    fun theNormalizedAddressIsWhatReachesTheRepository() {
        // The user types what they know; the repository receives a canonical
        // form.
        fillValidForm()
        repository.nextResult = successFor("rss.exemple.org")

        viewModel.submit()

        assertEquals("https://rss.exemple.org", repository.lastAddress?.baseUrl)
    }

    @Test
    fun theUsernameIsTrimmedButThePasswordIsNot() {
        // A space pasted along with the username must not fail the sign-in;
        // removing one from the password would.
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
        // Without this, a double press would send two sign-in requests.
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
        // A UiState outlives the screen that displays it: the password would
        // end up in a debug snapshot or a process restoration.
        fillValidForm()
        repository.nextResult = successFor("rss.exemple.org")

        viewModel.submit()

        assertEquals("", viewModel.uiState.value.apiPassword)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun aServerFailureIsSurfacedWithItsCause() {
        fillValidForm()
        repository.nextResult = Outcome.Failure(AuthError.ApiDisabled)

        viewModel.submit()

        val failure = assertIs<LoginFailure.Server>(viewModel.uiState.value.failure)
        assertEquals(AuthError.ApiDisabled, failure.error)
    }

    @Test
    fun aFailedAttemptKeepsWhatTheUserTyped() {
        // Retyping the address and username after every failure would be
        // painful, and the error usually concerns only the password.
        fillValidForm()
        repository.nextResult = Outcome.Failure(AuthError.InvalidCredentials)

        viewModel.submit()

        assertEquals("rss.exemple.org", viewModel.uiState.value.serverAddress)
        assertEquals("alice", viewModel.uiState.value.username)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun aNewAttemptClearsThePreviousFailure() {
        fillValidForm()
        repository.nextResult = Outcome.Failure(AuthError.NoNetwork)
        viewModel.submit()

        repository.nextResult = successFor("rss.exemple.org")
        viewModel.submit()

        assertNull(viewModel.uiState.value.failure)
    }

    private fun successFor(raw: String): Outcome.Success<fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession> {
        val address = (ServerAddress.parse(raw) as ServerAddressResult.Valid).address
        return Outcome.Success(repository.sessionOf(address))
    }
}
