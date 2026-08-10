package fr.vbrosseau.freshrssdiscover.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.createFreshRssHttpClient
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.room.AppDatabase
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.security.FakeSecretCipher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.core.errorOrNull
import fr.vbrosseau.freshrssdiscover.domain.read.FakeReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultAuthRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val address = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val credentials = Credentials("alice", "mot-de-passe-api")

    private var online = true

    /** Verifies that signing out empties the pending mark queue (SPECS.md §3.5). */
    private val readSyncRepository = FakeReadSyncRepository()

    private val requestedPaths = mutableListOf<String>()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionStore: SessionStore

    @After
    fun stopWriting() = scope.cancel()

    /** Answers each path according to [routes]; an unexpected path fails the test. */
    private fun repository(vararg routes: Pair<String, MockResponse>): DefaultAuthRepository {
        val byPath = routes.toMap()
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("session.preferences_pb").also(File::delete)
        }
        sessionStore = SessionStore(dataStore, FakeSecretCipher())

        val engine = MockEngine { request: HttpRequestData ->
            val path = request.url.encodedPath
            requestedPaths += path
            val route = byPath.entries.firstOrNull { path.endsWith(it.key) }?.value
                ?: error("chemin non prévu par le test : $path")
            when (route) {
                is MockResponse.Body -> respond(
                    content = route.text,
                    status = route.status,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8"),
                )

                is MockResponse.Failure -> throw route.cause
            }
        }

        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        return DefaultAuthRepository(
            api = FreshRssApi(createFreshRssHttpClient(engine)),
            sessionStore = sessionStore,
            articleCache = ArticleCache(database.articleDao(), Clock { 0L }),
            readSyncRepository = readSyncRepository,
            network = NetworkAvailability { online },
            ioDispatcher = dispatcher,
        )
    }

    private sealed interface MockResponse {
        data class Body(val text: String, val status: HttpStatusCode = HttpStatusCode.OK) : MockResponse
        data class Failure(val cause: Throwable) : MockResponse
    }

    private fun ok(text: String) = MockResponse.Body(text)
    private fun error(status: HttpStatusCode, text: String) = MockResponse.Body(text, status)

    private val validLogin = arrayOf(
        "/greader.php" to ok("OK"),
        "/accounts/ClientLogin" to ok("SID=alice/c0ffee\nLSID=null\nAuth=alice/c0ffee\n"),
        "/check/compatibility" to ok("PASS"),
    )

    // ----- Nominal path ------------------------------------------------------

    @Test
    fun aSuccessfulSignInReturnsAndPersistsTheSession() = runTest {
        val repository = repository(*validLogin)

        val result = repository.signIn(address, credentials)

        val session = assertIs<Outcome.Success<*>>(result).value
        assertNotNull(session)
        assertEquals("alice/c0ffee", assertNotNull(repository.observeSession().first()).token.value)
    }

    @Test
    fun theInstanceIsRecognizedBeforeAnyCredentialIsSent() = runTest {
        // Without this probe, a typo in the address would send the API password
        // to a server that is not the user's, and produce a 401 the user would
        // blame on their credentials.
        repository(*validLogin).signIn(address, credentials)

        val probeIndex = requestedPaths.indexOfFirst { it.endsWith("/greader.php") }
        val loginIndex = requestedPaths.indexOfFirst { it.endsWith("/accounts/ClientLogin") }
        assertTrue(probeIndex < loginIndex, "sonde après l'envoi des identifiants : $requestedPaths")
    }

    @Test
    fun nothingIsSentWhenTheAddressIsNotAFreshRssInstance() = runTest {
        val repository = repository("/greader.php" to error(HttpStatusCode.NotFound, "Not Found"))

        val result = repository.signIn(address, credentials)

        assertEquals(AuthError.NotAFreshRssServer, result.errorOrNull())
        assertTrue(requestedPaths.none { it.contains("ClientLogin") }, "identifiants envoyés malgré tout")
    }

    // ----- Sign-in failures --------------------------------------------------

    @Test
    fun refusedCredentialsAreReportedAsSuch() = runTest {
        val repository = repository(
            "/greader.php" to ok("OK"),
            "/accounts/ClientLogin" to error(HttpStatusCode.Unauthorized, "Unauthorized!"),
        )

        assertEquals(AuthError.InvalidCredentials, repository.signIn(address, credentials).errorOrNull())
    }

    @Test
    fun aDisabledApiIsDiagnosedEvenThoughTheProbeStillAnswersOk() = runTest {
        // Observed on a real instance with the API disabled: the recognition
        // probe still answers "OK" with 200, unchanged, because the shortcut
        // serving it sits before the `api_enabled` check in FreshRSS's router.
        // Concluding "valid server" from the probe alone would produce a wrong
        // diagnosis: ClientLogin is what reveals the 503.
        val repository = repository(
            "/greader.php" to ok("OK"),
            "/accounts/ClientLogin" to error(HttpStatusCode.ServiceUnavailable, "Service Unavailable!"),
        )

        assertEquals(AuthError.ApiDisabled, repository.signIn(address, credentials).errorOrNull())
    }

    @Test
    fun aDisabledApiIsAlsoDiagnosedWhenItAnswersOnTheProbeItself() = runTest {
        // A differently configured server, or a future version: if the 503
        // surfaces on the probe itself, the diagnosis must stay the same.
        val repository = repository("/greader.php" to error(HttpStatusCode.ServiceUnavailable, "Service Unavailable!"))

        assertEquals(AuthError.ApiDisabled, repository.signIn(address, credentials).errorOrNull())
    }

    @Test
    fun anUnreachableServerIsDistinguishedFromAnAbsentNetwork() = runTest {
        online = true
        val reachable = repository("/greader.php" to MockResponse.Failure(IOException("hôte inconnu")))
        assertEquals(AuthError.ServerUnreachable, reachable.signIn(address, credentials).errorOrNull())

        requestedPaths.clear()
        online = false
        val offline = repository("/greader.php" to MockResponse.Failure(IOException("hôte inconnu")))
        assertEquals(AuthError.NoNetwork, offline.signIn(address, credentials).errorOrNull())
    }

    // ----- Header not forwarded ----------------------------------------------

    @Test
    fun aProxyStrippingTheAuthorizationHeaderIsNamedRatherThanBlamedOnCredentials() = runTest {
        // Without this case, sign-in would succeed and every subsequent call
        // would fail with 401: the user would change their password in vain.
        val repository = repository(
            "/greader.php" to ok("OK"),
            "/accounts/ClientLogin" to ok("Auth=alice/c0ffee"),
            "/check/compatibility" to ok("FAIL get HTTP Authorization header! Wrong Web server configuration."),
        )

        val result = repository.signIn(address, credentials)

        assertEquals(AuthError.AuthorizationHeaderNotForwarded, result.errorOrNull())
    }

    @Test
    fun noSessionIsStoredWhenTheHeaderIsNotForwarded() = runTest {
        // Keeping a session doomed to fail on every call would put the app in
        // an unexplained 401 loop.
        val repository = repository(
            "/greader.php" to ok("OK"),
            "/accounts/ClientLogin" to ok("Auth=alice/c0ffee"),
            "/check/compatibility" to ok("FAIL"),
        )

        repository.signIn(address, credentials)

        assertNull(repository.observeSession().first())
    }

    @Test
    fun theForwardingCheckHappensOnlyAfterTheTokenIsObtained() = runTest {
        // ClientLogin requires no authorization header: checking beforehand
        // would cost a round trip on every sign-in, including attempts bound to
        // fail on the credentials.
        repository(*validLogin).signIn(address, credentials)

        val loginIndex = requestedPaths.indexOfFirst { it.endsWith("/accounts/ClientLogin") }
        val checkIndex = requestedPaths.indexOfFirst { it.endsWith("/check/compatibility") }
        assertTrue(loginIndex < checkIndex, "ordre inattendu : $requestedPaths")
    }

    @Test
    fun refusedCredentialsCostNoForwardingCheck() = runTest {
        repository(
            "/greader.php" to ok("OK"),
            "/accounts/ClientLogin" to error(HttpStatusCode.Unauthorized, "Unauthorized!"),
        ).signIn(address, credentials)

        assertTrue(requestedPaths.none { it.contains("compatibility") })
    }

    // ----- Sign-out ----------------------------------------------------------

    @Test
    fun signingOutAlsoEmptiesThePendingMarkQueue() = runTest {
        // These marks reference articles that no longer exist locally:
        // transmitting them after reconnecting on another account would be
        // worse than losing them.
        val repository = repository(*validLogin)
        repository.signIn(address, credentials)

        repository.signOut()

        assertEquals(1, readSyncRepository.clearPendingCallCount)
    }

    @Test
    fun signingOutErasesTheSession() = runTest {
        val repository = repository(*validLogin)
        repository.signIn(address, credentials)

        repository.signOut()

        assertNull(repository.observeSession().first())
    }
}
