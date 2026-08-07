package fr.vbrosseau.freshrssdiscover.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.createFreshRssHttpClient
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.room.AppDatabase
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.security.FakeSecretCipher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthResult
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.core.errorOrNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.rules.TemporaryFolder

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
    private val requestedPaths = mutableListOf<String>()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionStore: SessionStore

    @After
    fun stopWriting() = scope.cancel()

    /** Répond à chaque chemin d'après [routes] ; un chemin non prévu échoue le test. */
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

    // ----- Chemin nominal ----------------------------------------------------

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
        // Sans cette sonde, une faute de frappe dans l'adresse enverrait le mot
        // de passe API à un serveur qui n'est pas celui de l'utilisateur, et
        // produirait un 401 qu'il imputerait à ses identifiants.
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

    // ----- Échecs de connexion ----------------------------------------------

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
        // Constaté sur une instance réelle, API désactivée : la sonde de
        // reconnaissance répond « OK » et 200, **inchangée**. Le court-circuit
        // qui la sert est placé avant la vérification `api_enabled` dans le
        // routeur de FreshRSS.
        //
        // Conclure « serveur valide » sur cette seule sonde afficherait donc un
        // diagnostic faux : c'est ClientLogin qui révèle le 503.
        val repository = repository(
            "/greader.php" to ok("OK"),
            "/accounts/ClientLogin" to error(HttpStatusCode.ServiceUnavailable, "Service Unavailable!"),
        )

        assertEquals(AuthError.ApiDisabled, repository.signIn(address, credentials).errorOrNull())
    }

    @Test
    fun aDisabledApiIsAlsoDiagnosedWhenItAnswersOnTheProbeItself() = runTest {
        // Cas d'un serveur configuré autrement, ou d'une version future : si le
        // 503 remonte dès la sonde, le diagnostic doit rester le même.
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

    // ----- En-tête non transmis ---------------------------------------------

    @Test
    fun aProxyStrippingTheAuthorizationHeaderIsNamedRatherThanBlamedOnCredentials() = runTest {
        // Sans ce cas, la connexion réussirait puis chaque appel suivant
        // échouerait en 401 : l'utilisateur changerait son mot de passe en vain.
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
        // Conserver une session vouée à échouer à chaque appel ferait entrer
        // l'application dans une boucle de 401 sans explication.
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
        // ClientLogin n'exige aucun en-tête d'autorisation : vérifier avant
        // aurait coûté un aller-retour à chaque connexion, y compris aux
        // tentatives vouées à échouer sur les identifiants.
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

    // ----- Déconnexion -------------------------------------------------------

    @Test
    fun signingOutErasesTheSession() = runTest {
        val repository = repository(*validLogin)
        repository.signIn(address, credentials)

        repository.signOut()

        assertNull(repository.observeSession().first())
    }
}
