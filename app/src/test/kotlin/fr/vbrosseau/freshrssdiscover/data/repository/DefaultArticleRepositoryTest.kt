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
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.core.errorOrNull
import fr.vbrosseau.freshrssdiscover.domain.core.valueOrNull
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FakeFeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedError
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import fr.vbrosseau.freshrssdiscover.domain.feed.article
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plus large que ce que les tests écrivent : la borne n'est jamais ce qu'ils éprouvent. */
private const val CACHE_LIMIT = 100

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
    private var servedResponses = 0

    private lateinit var sessionStore: SessionStore
    private lateinit var dataStore: DataStore<Preferences>

    private val freshness = FakeFeedFreshnessRepository()

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

                is MockEngineResponse.Bodies -> respond(
                    content = respond.texts[servedResponses++],
                    status = HttpStatusCode.OK,
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
            cache = cache,
            freshness = freshness,
            network = NetworkAvailability { online },
            ioDispatcher = dispatcher,
        )
    }

    /**
     * Base en mémoire, partagée par le dépôt et par le test.
     *
     * Le test doit pouvoir garnir le cache **avant** que le dépôt n'existe :
     * c'est exactement la situation d'un lancement d'application, où le contenu
     * précède la première requête.
     */
    private val cache: ArticleCache by lazy {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        ArticleCache(database.articleDao(), Clock { 0L })
    }

    private sealed interface MockEngineResponse {
        data class Body(val text: String, val status: HttpStatusCode = HttpStatusCode.OK) : MockEngineResponse

        /** Réponses servies dans l'ordre, pour éprouver deux pages successives. */
        data class Bodies(val texts: List<String>) : MockEngineResponse

        data class Failure(val newCause: () -> Throwable) : MockEngineResponse
    }

    private suspend fun signedIn() {
        sessionStore.save(
            AuthSession(server = server, username = "alice", token = AuthToken("alice/c0ffee")),
        )
    }

    /**
     * Une page dont chaque article porte le flux demandé.
     *
     * Les identifiants voyagent en hexadécimal, les dates décroissent avec eux :
     * l'ordre du serveur est ainsi celui de la liste, et une inversion due au
     * mélange devient lisible dans l'assertion.
     */
    private fun page(
        vararg items: Pair<Long, String>,
        continuation: String? = null,
    ): String {
        val entries = items.joinToString(",") { (id, feedId) ->
            """
            {"id":"tag:google.com,2005:reader/item/${"%016x".format(id)}","title":"Article $id",
             "published":${1_700_000_000L - id},
             "origin":{"streamId":"$feedId","title":"Flux $feedId"}}
            """.trimIndent()
        }
        val tail = continuation?.let { ""","continuation":"$it"""" }.orEmpty()
        return """{"items":[$entries]$tail}"""
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
    fun theCachedFeedIsReadableBeforeAnyRequest() = runTest {
        // SPECS.md §5.1 : un écran vide pendant une requête donnerait
        // l'impression d'une application sans contenu, alors qu'elle en a.
        cache.save(listOf(article(id = 1L, title = "Déjà là")))
        val repository = repository(MockEngineResponse.Body(onePage))

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first()

        assertEquals(listOf("Déjà là"), cached.map { it.title })
        assertNull(lastRequest, "aucune requête ne doit précéder l'affichage du cache")
    }

    @Test
    fun readArticlesStayInTheFeedUntilTheNextReload() = runTest {
        // C'est l'astuce qui rend le lancement stable : un article lu ne
        // disparaît pas de l'écran à la prochaine ouverture. S'il partait,
        // l'ensemble changerait d'une session à l'autre — le marquage en
        // consomme à chaque lecture — et le mélange rendrait un ordre
        // différent : le flux paraîtrait se remélanger tout seul. Seul un
        // rechargement demandé (SPECS.md §4.6) renouvelle la liste.
        cache.save(listOf(article(id = 1L, title = "Lu", isRead = true), article(id = 2L, title = "Non lu")))
        val repository = repository(MockEngineResponse.Body(onePage))

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first()

        assertEquals(setOf("Lu", "Non lu"), cached.map { it.title }.toSet())
    }

    @Test
    fun theOrderOfTheCachedFeedDoesNotChangeWhenAnArticleIsRead() = runTest {
        // Le même invariant, vu de l'ordre : marquer un article lu ne doit
        // déplacer personne. C'est le défaut constaté sur appareil — trois
        // lancements consécutifs, trois têtes différentes, sans qu'aucune
        // requête ne soit partie.
        cache.save(List(6) { article(id = it + 1L, publishedAtEpochSeconds = 100L + it) })
        val repository = repository(MockEngineResponse.Body(onePage))
        val before = repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value }

        cache.markAsRead(listOf(ArticleId(before.first()), ArticleId(before[1])))

        assertEquals(before, repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value })
    }

    @Test
    fun theReminderStillOnlySeesUnreadArticles() = runTest {
        // Le flux garde les lus ; le rappel de lecture, lui, n'a rien à dire
        // sur ce qui est déjà lu (SPECS.md §4.9). Les deux lectures du cache ne
        // répondent pas à la même question.
        cache.save(listOf(article(id = 1L, title = "Lu", isRead = true), article(id = 2L, title = "Non lu")))
        val repository = repository(MockEngineResponse.Body(onePage))

        assertEquals(listOf("Non lu"), repository.unreadFromCache(CACHE_LIMIT).map { it.title })
    }

    @Test
    fun offlineTheCachedFeedRemainsReadable() = runTest {
        // SPECS.md §5.2 : sans réseau, le flux reste consultable.
        online = false
        cache.save(listOf(article(id = 1L, title = "Hors ligne")))
        val repository = repository(MockEngineResponse.Failure { IOException("pas de réseau") })
        signedIn()

        val result = repository.loadPage()

        assertEquals(FeedError.NoNetwork, result.errorOrNull())
        assertEquals(listOf("Hors ligne"), repository.observeCachedArticles(CACHE_LIMIT).first().map { it.title })
    }

    @Test
    fun offlineWithAnEmptyCacheIsAFailureNotAnEndOfFeed() = runTest {
        // Le piège : rendre le cache sous forme de page ferait afficher « vous
        // avez tout lu » à un utilisateur simplement privé de réseau, un
        // `nextCursor` nul ne signifiant rien d'autre que la fin du flux.
        online = false
        val repository = repository(MockEngineResponse.Failure { IOException("pas de réseau") })
        signedIn()

        val result = repository.loadPage()

        assertEquals(FeedError.NoNetwork, result.errorOrNull())
        assertNull(result.valueOrNull())
        assertTrue(repository.observeCachedArticles(CACHE_LIMIT).first().isEmpty())
    }

    @Test
    fun aNetworkPageFeedsTheCache() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage()

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first()
        assertEquals(listOf("Un titre"), cached.map { it.title })
    }

    @Test
    fun twoArticlesOfTheSameSourceAreSeparatedWhenAnotherSourceExists() = runTest {
        // SPECS.md §4.2, règle 1 : un flux prolifique ne doit pas occuper
        // l'écran d'affilée.
        val repository = repository(MockEngineResponse.Body(page(1L to "feed/1", 2L to "feed/1", 3L to "feed/2")))
        signedIn()

        val page = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(listOf("feed/1", "feed/2", "feed/1"), page.articles.map { it.feed.id })
    }

    @Test
    fun theMixKeepsItsContinuityAcrossTwoPages() = runTest {
        // Règle 4 : la jonction entre deux pages obéit à la règle 1. Le dépôt
        // est le seul à savoir comment il a ordonné la page précédente.
        val repository = repository(
            MockEngineResponse.Bodies(
                listOf(
                    page(1L to "feed/1", continuation = "45219"),
                    page(2L to "feed/1", 3L to "feed/2"),
                ),
            ),
        )
        signedIn()

        val first = assertNotNull(repository.loadPage().valueOrNull())
        val second = assertNotNull(repository.loadPage(first.nextCursor).valueOrNull())

        assertEquals("feed/1", first.articles.single().feed.id)
        assertEquals(listOf("feed/2", "feed/1"), second.articles.map { it.feed.id })
    }

    @Test
    fun askingForTheFirstPageAgainRestartsTheMixFromScratch() = runTest {
        // Règle 3 : le même ensemble d'articles produit le même ordre. Une fin
        // de page rémanente ferait dépendre le premier écran de ce qui a été
        // affiché avant lui.
        val body = page(1L to "feed/1", 2L to "feed/1", 3L to "feed/2")
        val repository = repository(MockEngineResponse.Body(body))
        signedIn()

        val first = assertNotNull(repository.loadPage().valueOrNull())
        val again = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(first.articles, again.articles)
    }

    @Test
    fun refreshAsksForTheHeadOfTheFeed() = runTest {
        // SPECS.md §4.6 : le rafraîchissement redemande le début, sans curseur.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage(cursor = PageCursor("45219"))
        repository.refresh()

        assertNull(lastRequest?.url?.parameters?.get("c"))
    }

    @Test
    fun refreshLosesNoArticle() = runTest {
        // Le mélange est une permutation exacte : rafraîchir ne doit rien
        // écarter, pas même un article déjà affiché.
        val repository = repository(MockEngineResponse.Body(page(1L to "feed/1", 2L to "feed/1", 3L to "feed/2")))
        signedIn()

        val refreshed = assertNotNull(repository.refresh().valueOrNull())

        assertEquals(setOf(1L, 2L, 3L), refreshed.articles.map { it.id.value }.toSet())
    }

    @Test
    fun refreshDoesNotDisturbThePaginationContinuity() = runTest {
        // La page rafraîchie est la tête du flux ; la fin de page conservée
        // décrit son bas. Les confondre réordonnerait la jonction suivante.
        val repository = repository(
            MockEngineResponse.Bodies(
                listOf(
                    page(1L to "feed/1", continuation = "45219"),
                    page(9L to "feed/2"),
                    page(2L to "feed/1", 3L to "feed/2"),
                ),
            ),
        )
        signedIn()

        val first = assertNotNull(repository.loadPage().valueOrNull())
        repository.refresh()
        val second = assertNotNull(repository.loadPage(first.nextCursor).valueOrNull())

        assertEquals(listOf("feed/2", "feed/1"), second.articles.map { it.feed.id })
    }

    @Test
    fun aSuccessCarriesNoError() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        val result = repository.loadPage()

        assertTrue(result is Outcome.Success)
        assertNull(result.errorOrNull())
    }

    @Test
    fun aPageServedInCrawlOrderIsShownInPublicationOrder() = runTest {
        // Constaté sur une instance réelle : le serveur trie sa reading-list par
        // date de récupération, et un article publié deux jours plus tôt peut
        // ouvrir la première page. Deux conséquences si on l'affiche tel quel :
        // l'ordre à l'écran dépend de qui, du disque ou du réseau, répond en
        // premier au lancement — le cache, lui, trie par publication — et la
        // reprise de lecture (SPECS.md §5.3), qui cherche « le premier article
        // pas plus récent », tombe n'importe où dans une liste non
        // chronologique. La page est donc ramenée à l'ordre de publication ici,
        // avant le mélange, qui l'attend déjà (règle 2 de SPECS.md §4.2).
        val repository = repository(
            MockEngineResponse.Body(
                pageWithDates(
                    Triple(1L, "feed/1", 100L),
                    Triple(2L, "feed/1", 300L),
                    Triple(3L, "feed/1", 200L),
                ),
            ),
        )
        signedIn()

        val page = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(listOf(2L, 3L, 1L), page.articles.map { it.id.value })
    }

    @Test
    fun articlesPublishedAtTheSameSecondFollowTheCacheTieBreak() = runTest {
        // À date égale, id décroissant — le même départage que le tri SQL du
        // cache. Deux départages différents referaient dépendre l'ordre affiché
        // de la source qui a répondu en premier.
        val repository = repository(
            MockEngineResponse.Body(
                pageWithDates(
                    Triple(1L, "feed/1", 100L),
                    Triple(2L, "feed/1", 100L),
                ),
            ),
        )
        signedIn()

        val page = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(listOf(2L, 1L), page.articles.map { it.id.value })
    }

    /** Une page dont la date de publication est **explicite**, découplée de l'id. */
    private fun pageWithDates(vararg items: Triple<Long, String, Long>): String {
        val entries = items.joinToString(",") { (id, feedId, published) ->
            """
            {"id":"tag:google.com,2005:reader/item/${"%016x".format(id)}","title":"Article $id",
             "published":$published,
             "origin":{"streamId":"$feedId","title":"Flux $feedId"}}
            """.trimIndent()
        }
        return """{"items":[$entries]}"""
    }

    @Test
    fun aPageObtainedFromTheServerRecordsTheContact() = runTest {
        // SPECS.md §4.6 : c'est cette date qui dira si le flux affiché est
        // ancien. Une page ordinaire compte autant qu'un rafraîchissement.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()
        freshness.nowEpochMillis = 1_700_000_000_000L

        repository.loadPage()

        assertEquals(1, freshness.recordCallCount)
        assertEquals(1_700_000_000_000L, freshness.current.lastRefreshEpochMillis)
    }

    @Test
    fun aRefreshRecordsTheContact() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.refresh()

        assertEquals(1, freshness.recordCallCount)
    }

    @Test
    fun anEmptyButValidPageStillRecordsTheContact() = runTest {
        // Le serveur a répondu ; c'est le flux qui n'a rien de neuf. Ne rien
        // noter ferait paraître ancien un flux qu'on vient d'épuiser.
        val repository = repository(MockEngineResponse.Body("""{"items":[]}"""))
        signedIn()

        repository.loadPage()

        assertEquals(1, freshness.recordCallCount)
    }

    @Test
    fun anExpiredSessionRecordsNoContact() = runTest {
        val repository = repository(
            MockEngineResponse.Body(onePage, status = HttpStatusCode.Unauthorized),
        )
        signedIn()

        repository.loadPage()

        assertEquals(0, freshness.recordCallCount)
    }

    @Test
    fun aServerErrorRecordsNoContact() = runTest {
        val repository = repository(
            MockEngineResponse.Body(onePage, status = HttpStatusCode.InternalServerError),
        )
        signedIn()

        repository.loadPage()

        assertEquals(0, freshness.recordCallCount)
    }

    @Test
    fun anOfflineFailureRecordsNoContact() = runTest {
        // Sans cette règle, une application laissée ouverte hors ligne toute la
        // journée paraîtrait fraîche.
        val repository = repository(MockEngineResponse.Failure { IOException("réseau") })
        signedIn()
        online = false

        repository.loadPage()

        assertEquals(0, freshness.recordCallCount)
    }

    @Test
    fun aMalformedAnswerRecordsNoContact() = runTest {
        val repository = repository(MockEngineResponse.Body("pas du JSON"))
        signedIn()

        repository.loadPage()

        assertEquals(0, freshness.recordCallCount)
    }

    @Test
    fun withoutASessionNothingIsRecorded() = runTest {
        val repository = repository(MockEngineResponse.Body(onePage))

        repository.loadPage()

        assertEquals(0, freshness.recordCallCount)
    }
}
