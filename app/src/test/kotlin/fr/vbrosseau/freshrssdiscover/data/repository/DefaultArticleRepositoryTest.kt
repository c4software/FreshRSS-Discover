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
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.core.errorOrNull
import fr.vbrosseau.freshrssdiscover.domain.core.valueOrNull
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultArticleRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private var online = true
    private var lastRequest: HttpRequestData? = null

    private lateinit var sessionStore: SessionStore
    private lateinit var dataStore: DataStore<Preferences>

    @After
    fun stopWriting() = scope.cancel()

    private fun repository(respond: MockEngineResponse): DefaultArticleRepository {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("session.preferences_pb").also(File::delete)
        }
        sessionStore = SessionStore(dataStore, FakeSecretCipher())

        val engine = MockEngine { request ->
            lastRequest = request
            when (respond) {
                is MockEngineResponse.Body -> respond(
                    content = respond.text,
                    status = respond.status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )

                // Une **nouvelle** exception à chaque appel : relancer la même
                // instance la fait décorer deux fois par la pile de coroutines,
                // et elle finit par échapper au rattrapage.
                is MockEngineResponse.Failure -> throw respond.newCause()
            }
        }

        return DefaultArticleRepository(
            api = FreshRssApi(createFreshRssHttpClient(engine)),
            sessionStore = sessionStore,
            cache = articleCache(),
            network = NetworkAvailability { online },
            ioDispatcher = dispatcher,
        )
    }

    /** Base en mémoire : le dépôt dépose chaque page au cache avant de la rendre. */
    private fun articleCache(): ArticleCache {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        return ArticleCache(database.articleDao(), Clock { 0L })
    }

    private sealed interface MockEngineResponse {
        data class Body(val text: String, val status: HttpStatusCode = HttpStatusCode.OK) : MockEngineResponse

        data class Failure(val newCause: () -> Throwable) : MockEngineResponse
    }

    private suspend fun signedIn() {
        sessionStore.save(
            AuthSession(server = server, username = "alice", token = AuthToken("alice/c0ffee")),
        )
    }

    private val onePage = """
        {"id":"user/-/state/com.google/reading-list","updated":1,
         "items":[{"id":"tag:google.com,2005:reader/item/0000000000000001",
                   "title":"Un titre","published":1699999000,
                   "canonical":[{"href":"https://exemple.org/a"}],
                   "categories":["user/-/state/com.google/reading-list"],
                   "origin":{"streamId":"feed/1","title":"Flux"},
                   "summary":{"content":"<p>Extrait.</p>"}}],
         "continuation":"45219"}
    """.trimIndent()

    @Test
    fun aPageIsReadAndConvertedToTheDomain() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        val page = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(1, page.articles.size)
        assertEquals("Un titre", page.articles.single().title)
        assertEquals("Extrait.", page.articles.single().summary)
        assertEquals(PageCursor("45219"), page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun theSessionTokenIsSentWithTheRequest() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage()

        assertEquals(
            "GoogleLogin auth=alice/c0ffee",
            lastRequest?.headers?.get(HttpHeaders.Authorization),
        )
    }

    @Test
    fun fortyArticlesAreAskedPerPage() = runTest {
        // Tranché dans SPECS.md §8 : mesuré sur un flux réel, une page de 40
        // pèse environ 55 ko.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage()

        assertEquals("40", lastRequest?.url?.parameters?.get("n"))
    }

    @Test
    fun readArticlesAreExcluded() = runTest {
        // SPECS.md §4.1 : le flux ne présente que les articles non lus.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage()

        assertEquals("user/-/state/com.google/read", lastRequest?.url?.parameters?.get("xt"))
    }

    @Test
    fun noCursorIsSentForTheFirstPage() = runTest {
        // Un `c` vide est silencieusement ramené au début du flux : l'envoyer
        // produirait une boucle infinie muette, jamais une erreur.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage(cursor = null)

        assertNull(lastRequest?.url?.parameters?.get("c"))
    }

    @Test
    fun theCursorIsForwardedForFollowingPages() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage(cursor = PageCursor("45219"))

        assertEquals("45219", lastRequest?.url?.parameters?.get("c"))
    }

    @Test
    fun anAbsentContinuationEndsTheFeed() = runTest {
        val repository = repository(MockEngineResponse.Body("""{"items":[]}"""))
        signedIn()

        val page = assertNotNull(repository.loadPage().valueOrNull())

        assertNull(page.nextCursor)
        assertTrue(page.articles.isEmpty())
    }

    @Test
    fun loadingWithoutASessionIsReportedRatherThanShownAsAnEmptyFeed() = runTest {
        // Une page vide se lirait « plus d'articles », ce qui est faux et
        // définitif du point de vue de l'utilisateur.
        val repository = repository(MockEngineResponse.Body(onePage))

        assertEquals(FeedError.SessionExpired, repository.loadPage().errorOrNull())
    }

    @Test
    fun aRefusedTokenEndsTheSessionSoTheRootRedirectsByItself() = runTest {
        // Lève GOAL-002-T20 : c'est le premier appel authentifié, donc le
        // premier déclencheur réel de l'invalidation.
        val repository = repository(
            MockEngineResponse.Body("Unauthorized!", HttpStatusCode.Unauthorized),
        )
        signedIn()

        assertEquals(FeedError.SessionExpired, repository.loadPage().errorOrNull())
        assertNull(sessionStore.observeSession().first())
    }

    @Test
    fun aRefusedTokenKeepsTheSignInHintSoNothingHasToBeRetyped() = runTest {
        val repository = repository(
            MockEngineResponse.Body("Unauthorized!", HttpStatusCode.Unauthorized),
        )
        signedIn()

        repository.loadPage()

        val hint = assertNotNull(sessionStore.observeLastSignInHint().first())
        assertEquals("alice", hint.username)
        assertEquals("https://exemple.org", hint.server.baseUrl)
    }

    @Test
    fun aServerThatDoesNotAnswerIsReportedAsUnreachable() = runTest {
        online = true
        val repository = repository(MockEngineResponse.Failure { IOException("délai dépassé") })
        signedIn()

        assertEquals(FeedError.ServerUnreachable, repository.loadPage().errorOrNull())
    }

    @Test
    fun theSameFailureWithoutNetworkIsReportedAsBeingOffline() = runTest {
        // La pile HTTP rapporte les deux de façon identique ; seule la
        // connectivité constatée les sépare, et les gestes de correction n'ont
        // rien à voir — attendre le réseau, ou vérifier son serveur.
        online = false
        val repository = repository(MockEngineResponse.Failure { IOException("délai dépassé") })
        signedIn()

        assertEquals(FeedError.NoNetwork, repository.loadPage().errorOrNull())
    }

    @Test
    fun aTruncatedResponseIsUnexpectedNotAnEmptyFeed() = runTest {
        // La confondre avec une fin de flux ferait disparaître les articles
        // sans que rien ne le signale.
        val repository = repository(MockEngineResponse.Body("""{"items":[{"id":"tag"""))
        signedIn()

        val error = repository.loadPage().errorOrNull()

        assertTrue(error is FeedError.Unexpected, "obtenu $error")
    }

    @Test
    fun aServerErrorKeepsItsCodeForTheLogs() = runTest {
        val repository = repository(
            MockEngineResponse.Body("Internal Server Error", HttpStatusCode.InternalServerError),
        )
        signedIn()

        val error = repository.loadPage().errorOrNull()

        assertTrue(error is FeedError.Unexpected && "500" in error.technicalMessage, "obtenu $error")
    }

    @Test
    fun aServerErrorDoesNotEndTheSession() = runTest {
        // Seul un 401 signifie « jeton refusé ». Effacer la session sur un 500
        // déconnecterait l'utilisateur à la moindre panne serveur.
        val repository = repository(
            MockEngineResponse.Body("Internal Server Error", HttpStatusCode.InternalServerError),
        )
        signedIn()

        repository.loadPage()

        assertNotNull(sessionStore.observeSession().first())
    }

    @Test
    fun anArticleWithAnUnreadableIdDoesNotDropTheWholePage() = runTest {
        val repository = repository(
            MockEngineResponse.Body(
                """
                {"items":[
                  {"id":"identifiant-illisible","title":"Écarté"},
                  {"id":"tag:google.com,2005:reader/item/0000000000000002","title":"Conservé",
                   "origin":{"streamId":"feed/1","title":"Flux"}}
                ]}
                """.trimIndent(),
            ),
        )
        signedIn()

        val page = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(listOf("Conservé"), page.articles.map { it.title })
    }

    @Test
    fun aSuccessCarriesNoError() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        val result = repository.loadPage()

        assertTrue(result is Outcome.Success)
        assertNull(result.errorOrNull())
    }
}
