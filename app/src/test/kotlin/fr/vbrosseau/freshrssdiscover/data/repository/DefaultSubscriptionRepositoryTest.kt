package fr.vbrosseau.freshrssdiscover.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssSubscriptionApi
import fr.vbrosseau.freshrssdiscover.data.api.createFreshRssHttpClient
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.security.FakeSecretCipher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.core.errorOrNull
import fr.vbrosseau.freshrssdiscover.domain.core.valueOrNull
import fr.vbrosseau.freshrssdiscover.domain.subscription.FeedUrl
import fr.vbrosseau.freshrssdiscover.domain.subscription.FeedUrlResult
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionError
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
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
import org.junit.Before
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

private const val LIST_BODY = """{"subscriptions":[
  {"id":"feed/12","title":"Le Monde","categories":[],"url":"https://www.lemonde.fr/rss/une.xml","htmlUrl":"https://www.lemonde.fr/","iconUrl":""},
  {"id":"feed/3","title":"XKCD","categories":[],"url":"https://xkcd.com/atom.xml","htmlUrl":"https://xkcd.com/","iconUrl":""}
]}"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultSubscriptionRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address

    private data class Reply(
        val body: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
        val json: Boolean = false,
    )

    /** Next reply, whatever the endpoint; `IOException` when [transportFailure] is set. */
    private var reply = Reply(LIST_BODY, json = true)
    private var transportFailure = false
    private var online = true

    private val requests = mutableListOf<HttpRequestData>()

    private val engine = MockEngine { request ->
        requests += request
        if (transportFailure) throw IOException("hôte inconnu")
        respond(
            content = reply.body,
            status = reply.status,
            headers = headersOf(
                HttpHeaders.ContentType,
                if (reply.json) "application/json; charset=UTF-8" else "text/plain; charset=UTF-8",
            ),
        )
    }

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: DefaultSubscriptionRepository

    @Before
    fun buildRepository() {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("session.preferences_pb").also(File::delete)
        }
        sessionStore = SessionStore(dataStore, FakeSecretCipher())
        repository = DefaultSubscriptionRepository(
            api = FreshRssSubscriptionApi(createFreshRssHttpClient(engine)),
            sessionStore = sessionStore,
            network = { online },
            ioDispatcher = dispatcher,
        )
    }

    @After
    fun releaseResources() {
        scope.cancel()
    }

    private suspend fun signedIn() {
        sessionStore.save(AuthSession(server = server, username = "alice", token = AuthToken("alice/c0ffee")))
    }

    private fun url(raw: String): FeedUrl = assertIs<FeedUrlResult.Valid>(FeedUrl.parse(raw)).url

    private fun lastForm() = (requests.last().body as FormDataContent).formData

    // ----- List --------------------------------------------------------------

    @Test
    fun theListingTranslatesStreamNamesIntoNumericIdentifiers() = runTest {
        signedIn()

        val listed = repository.list().valueOrNull()

        assertEquals(
            listOf(
                Subscription(SubscriptionId(12L), "Le Monde", "https://www.lemonde.fr/rss/une.xml"),
                Subscription(SubscriptionId(3L), "XKCD", "https://xkcd.com/atom.xml"),
            ),
            listed,
        )
    }

    @Test
    fun anEntryWithAnUnexpectedStreamNameIsDroppedNotFatal() = runTest {
        // Only `feed/<decimal>` was ever observed; one odd entry must not
        // hide the rest of the list, and removal needs the number anyway.
        signedIn()
        reply = Reply(
            """{"subscriptions":[{"id":"feed/https://x.org/rss","title":"?","url":"https://x.org/rss"},
               {"id":"feed/3","title":"XKCD","url":"https://xkcd.com/atom.xml"}]}""",
            json = true,
        )

        val listed = repository.list().valueOrNull()

        assertEquals(listOf(Subscription(SubscriptionId(3L), "XKCD", "https://xkcd.com/atom.xml")), listed)
    }

    @Test
    fun withoutASessionNothingIsAskedAndTheSessionIsReportedExpired() = runTest {
        assertEquals(SubscriptionError.SessionExpired, repository.list().errorOrNull())
        assertEquals(emptyList(), requests)
    }

    @Test
    fun aRejectedTokenClosesTheSessionAndSaysSo() = runTest {
        // The root router acts on the wiped session (SPECS.md §3.4); the
        // sign-in hint survives so nothing has to be retyped.
        signedIn()
        reply = Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        assertEquals(SubscriptionError.SessionExpired, repository.list().errorOrNull())

        assertNull(sessionStore.observeSession().first())
        assertNotNull(sessionStore.observeLastSignInHint().first())
    }

    @Test
    fun aTransportFailureIsNoNetworkWhenTheDeviceIsOffline() = runTest {
        signedIn()
        transportFailure = true
        online = false

        assertEquals(SubscriptionError.NoNetwork, repository.list().errorOrNull())
    }

    @Test
    fun aTransportFailureIsAnUnreachableServerWhenTheDeviceIsOnline() = runTest {
        signedIn()
        transportFailure = true

        assertEquals(SubscriptionError.ServerUnreachable, repository.list().errorOrNull())
    }

    @Test
    fun anUnreadableListIsUnexpected() = runTest {
        signedIn()
        reply = Reply("<html>maintenance</html>")

        assertIs<SubscriptionError.Unexpected>(repository.list().errorOrNull())
    }

    @Test
    fun aServerErrorIsUnexpectedWithItsStatus() = runTest {
        signedIn()
        reply = Reply("Internal Server Error!", HttpStatusCode.InternalServerError)

        assertEquals(SubscriptionError.Unexpected("HTTP 500"), repository.list().errorOrNull())
    }

    // ----- Subscribe ---------------------------------------------------------

    @Test
    fun subscribingSendsTheNormalisedAddress() = runTest {
        signedIn()
        reply = Reply("OK")

        val outcome = repository.subscribe(url("xkcd.com/atom.xml"))

        assertNull(outcome.errorOrNull())
        assertEquals("feed/https://xkcd.com/atom.xml", lastForm()["s"])
        assertEquals("subscribe", lastForm()["ac"])
    }

    @Test
    fun anAddressTheServerRefusesIsRejected() = runTest {
        signedIn()
        reply = Reply("Bad Request!", HttpStatusCode.BadRequest)

        assertEquals(SubscriptionError.Rejected, repository.subscribe(url("exemple.org/pas-un-flux")).errorOrNull())
        // A refusal is not a session problem: the session stays.
        assertNotNull(sessionStore.observeSession().first())
    }

    // ----- Unsubscribe -------------------------------------------------------

    @Test
    fun unsubscribingSendsTheStreamNameBuiltFromTheIdentifier() = runTest {
        signedIn()
        reply = Reply("OK")

        val outcome = repository.unsubscribe(SubscriptionId(12L))

        assertNull(outcome.errorOrNull())
        assertEquals("feed/12", lastForm()["s"])
        assertEquals("unsubscribe", lastForm()["ac"])
    }

    @Test
    fun removingAnIdentifierTheServerNoLongerKnowsIsRejected() = runTest {
        signedIn()
        reply = Reply("Bad Request!", HttpStatusCode.BadRequest)

        assertEquals(SubscriptionError.Rejected, repository.unsubscribe(SubscriptionId(999L)).errorOrNull())
    }

    @Test
    fun aRejectedTokenOnARemovalClosesTheSessionToo() = runTest {
        signedIn()
        reply = Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        assertEquals(SubscriptionError.SessionExpired, repository.unsubscribe(SubscriptionId(12L)).errorOrNull())
        assertNull(sessionStore.observeSession().first())
    }
}
