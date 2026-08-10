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
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkQueue
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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

/** Larger than what the tests write: the limit is never what they exercise. */
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

                // A fresh exception on each call: rethrowing the same
                // instance gets it decorated twice by the coroutine stack,
                // and it ends up escaping the catch.
                is MockEngineResponse.Failure -> throw respond.newCause()

                is MockEngineResponse.Gated -> {
                    respond.gate.await()
                    respond(
                        content = respond.text,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
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
     * In-memory database shared by the repository and the test.
     *
     * The test must be able to populate the cache before the repository
     * exists: exactly the app-launch situation, where content precedes the
     * first request.
     */
    private val database: AppDatabase by lazy {
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    private val cache: ArticleCache by lazy { ArticleCache(database.articleDao(), Clock { 0L }) }

    /**
     * The pending-mark queue, on the same database as the cache.
     *
     * This is what lets the test exercise what the reload purge spares: the
     * condition lives in a SQL subquery, and two separate databases would
     * make it always true, letting the case pass without proving anything.
     */
    private val pendingMarks: PendingMarkQueue by lazy {
        PendingMarkQueue(database.pendingMarkDao(), Clock { 0L })
    }

    private sealed interface MockEngineResponse {
        data class Body(val text: String, val status: HttpStatusCode = HttpStatusCode.OK) : MockEngineResponse

        /** Responses served in order, to exercise two successive pages. */
        data class Bodies(val texts: List<String>) : MockEngineResponse

        data class Failure(val newCause: () -> Throwable) : MockEngineResponse

        /** Response held until the test releases it: reproduces an in-flight page. */
        data class Gated(val gate: CompletableDeferred<Unit>, val text: String) : MockEngineResponse
    }

    private suspend fun signedIn() {
        sessionStore.save(
            AuthSession(server = server, username = "alice", token = AuthToken("alice/c0ffee")),
        )
    }

    /**
     * A page whose every article carries the requested feed.
     *
     * Ids travel in hexadecimal and dates decrease with them: the server
     * order matches the list order, so a mix-induced inversion is readable
     * in the assertion.
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
    fun aCancelledPageInFlightWritesNothingToTheCache() = runTest {
        // Reload cancellation (GOAL-029) promises more than dropping the
        // result: the request is abandoned, so its `cache.save` never runs
        // and the rows `retainOnly` just removed do not come back. This is
        // what the GOAL-028 counter did not cover.
        val gate = CompletableDeferred<Unit>()
        val repository = repository(MockEngineResponse.Gated(gate, onePage))
        signedIn()

        val flight = launch { repository.loadPage() }
        runCurrent()
        flight.cancel()
        gate.complete(Unit)
        flight.join()

        assertTrue(cache.observeArticles(CACHE_LIMIT).first().isEmpty())
    }

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
        // Decided in SPECS.md §8: measured on a real feed, a page of 40
        // weighs about 55 kB.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage()

        assertEquals("40", lastRequest?.url?.parameters?.get("n"))
    }

    @Test
    fun readArticlesAreExcluded() = runTest {
        // SPECS.md §4.1: the feed only presents unread articles.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage()

        assertEquals("user/-/state/com.google/read", lastRequest?.url?.parameters?.get("xt"))
    }

    @Test
    fun noCursorIsSentForTheFirstPage() = runTest {
        // An empty `c` is silently reset to the start of the feed: sending
        // one would produce a silent infinite loop, never an error.
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
        // An empty page would read as "no more articles", which is false and
        // final from the user's point of view.
        val repository = repository(MockEngineResponse.Body(onePage))

        assertEquals(FeedError.SessionExpired, repository.loadPage().errorOrNull())
    }

    @Test
    fun aRefusedTokenEndsTheSessionSoTheRootRedirectsByItself() = runTest {
        // Closes GOAL-002-T20: this is the first authenticated call, hence
        // the first real trigger of invalidation.
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
        // The HTTP stack reports both identically; only observed
        // connectivity tells them apart, and the fixes are unrelated: wait
        // for the network, or check the server.
        online = false
        val repository = repository(MockEngineResponse.Failure { IOException("délai dépassé") })
        signedIn()

        assertEquals(FeedError.NoNetwork, repository.loadPage().errorOrNull())
    }

    @Test
    fun aTruncatedResponseIsUnexpectedNotAnEmptyFeed() = runTest {
        // Mistaking it for an end of feed would make articles vanish without
        // any signal.
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
        // Only a 401 means a refused token. Clearing the session on a 500
        // would log the user out on any server outage.
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
        // SPECS.md §5.1: an empty screen during a request would suggest an
        // app with no content when it has some.
        cache.save(listOf(article(id = 1L, title = "Déjà là")))
        val repository = repository(MockEngineResponse.Body(onePage))

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first()

        assertEquals(listOf("Déjà là"), cached.map { it.title })
        assertNull(lastRequest, "aucune requête ne doit précéder l'affichage du cache")
    }

    @Test
    fun readArticlesStayInTheFeedUntilTheNextReload() = runTest {
        // What makes launch stable: a read article does not vanish from the
        // screen at the next opening. If it left, the set would change
        // between sessions and the mix would produce a different order, so
        // the feed would seem to reshuffle by itself. Only a requested
        // reload (SPECS.md §4.6) renews the list.
        cache.save(listOf(article(id = 1L, title = "Lu", isRead = true), article(id = 2L, title = "Non lu")))
        val repository = repository(MockEngineResponse.Body(onePage))

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first()

        assertEquals(setOf("Lu", "Non lu"), cached.map { it.title }.toSet())
    }

    @Test
    fun theOrderOfTheCachedFeedDoesNotChangeWhenAnArticleIsRead() = runTest {
        // The same invariant, from the ordering side: marking an article
        // read must move nothing. This was the defect observed on device:
        // three consecutive launches, three different heads, without a
        // single request sent.
        cache.save(List(6) { article(id = it + 1L, publishedAtEpochSeconds = 100L + it) })
        val repository = repository(MockEngineResponse.Body(onePage))
        val before = repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value }

        cache.markAsRead(listOf(ArticleId(before.first()), ArticleId(before[1])))

        assertEquals(before, repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value })
    }

    @Test
    fun theReminderStillOnlySeesUnreadArticles() = runTest {
        // The feed keeps read articles; the reading reminder has nothing to
        // say about what is already read (SPECS.md §4.9). The two cache
        // reads answer different questions.
        cache.save(listOf(article(id = 1L, title = "Lu", isRead = true), article(id = 2L, title = "Non lu")))
        val repository = repository(MockEngineResponse.Body(onePage))

        assertEquals(listOf("Non lu"), repository.unreadFromCache(CACHE_LIMIT).map { it.title })
    }

    @Test
    fun offlineTheCachedFeedRemainsReadable() = runTest {
        // SPECS.md §5.2: without network, the feed stays readable.
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
        // The trap: returning the cache as a page would show "you have read
        // everything" to a user who merely lost network, since a null
        // `nextCursor` means nothing but the end of the feed.
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
        // SPECS.md §4.2, rule 1: a prolific feed must not occupy the screen
        // consecutively.
        val repository = repository(MockEngineResponse.Body(page(1L to "feed/1", 2L to "feed/1", 3L to "feed/2")))
        signedIn()

        val page = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(listOf("feed/1", "feed/2", "feed/1"), page.articles.map { it.feed.id })
    }

    @Test
    fun theMixKeepsItsContinuityAcrossTwoPages() = runTest {
        // Rule 4: the junction between two pages obeys rule 1. The previous
        // page's tail is passed by the caller; the repository retains
        // nothing, being shared by both presentation modes.
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
        val second = assertNotNull(
            repository.loadPage(first.nextCursor, previousTail = first.articles.takeLast(1)).valueOrNull(),
        )

        assertEquals("feed/1", first.articles.single().feed.id)
        assertEquals(listOf("feed/2", "feed/1"), second.articles.map { it.feed.id })
    }

    @Test
    fun askingForTheFirstPageAgainRestartsTheMixFromScratch() = runTest {
        // Rule 3: the same set of articles produces the same order. A
        // lingering page tail would make the first screen depend on what was
        // displayed before it.
        val body = page(1L to "feed/1", 2L to "feed/1", 3L to "feed/2")
        val repository = repository(MockEngineResponse.Body(body))
        signedIn()

        val first = assertNotNull(repository.loadPage().valueOrNull())
        val again = assertNotNull(repository.loadPage().valueOrNull())

        assertEquals(first.articles, again.articles)
    }

    @Test
    fun refreshAsksForTheHeadOfTheFeed() = runTest {
        // SPECS.md §4.6: refresh re-requests the head, without a cursor.
        val repository = repository(MockEngineResponse.Body(onePage))
        signedIn()

        repository.loadPage(cursor = PageCursor("45219"))
        repository.refresh()

        assertNull(lastRequest?.url?.parameters?.get("c"))
    }

    @Test
    fun refreshLosesNoArticle() = runTest {
        // The mix is an exact permutation: refreshing must discard nothing,
        // not even an already-displayed article.
        val repository = repository(MockEngineResponse.Body(page(1L to "feed/1", 2L to "feed/1", 3L to "feed/2")))
        signedIn()

        val refreshed = assertNotNull(repository.refresh().valueOrNull())

        assertEquals(setOf(1L, 2L, 3L), refreshed.articles.map { it.id.value }.toSet())
    }

    // ----- Reload renews the cache (GOAL-026) ---------------------------------

    /**
     * The reported defect: empty the feed, kill the app, relaunch, and find
     * the set of articles just exhausted. The reload cleared the display
     * without touching the database.
     */
    @Test
    fun aReloadDropsFromTheCacheWhatHasAlreadyBeenRead() = runTest {
        cache.save(listOf(article(id = 1L, title = "Lu", isRead = true)))
        val repository = repository(MockEngineResponse.Body(page(2L to "feed/1")))
        signedIn()

        repository.refresh()

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value }
        assertEquals(listOf(2L), cached, "l'article lu doit disparaître du cache, pas seulement de l'écran")
    }

    /**
     * The case GOAL-026 could not see: an article the server no longer
     * returns goes away, even if locally unread. It was read elsewhere (web
     * UI, another client), and its absence from the returned page is the
     * only signal the app ever receives.
     *
     * Observed: after a reload showing nothing to read, 31 unread rows
     * remained in the database, and the next launch brought them back.
     */
    @Test
    fun aReloadDropsAnUnreadArticleTheServerNoLongerReturns() = runTest {
        cache.save(listOf(article(id = 7L, title = "Lu ailleurs")))
        val repository = repository(MockEngineResponse.Body(page(2L to "feed/1")))
        signedIn()

        repository.refresh()

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value }
        assertEquals(listOf(2L), cached, "le critère est la page rendue, pas l'état lu local")
    }

    /** The exact reported case: nothing left to read, so nothing left in the database. */
    @Test
    fun aReloadThatReturnsNothingEmptiesTheCache() = runTest {
        cache.save(listOf(article(id = 7L), article(id = 8L, isRead = true)))
        val repository = repository(MockEngineResponse.Body("""{"items":[]}"""))
        signedIn()

        repository.refresh()

        assertTrue(
            repository.observeCachedArticles(CACHE_LIMIT).first().isEmpty(),
            "un flux vidé doit le rester après avoir tué l'application",
        )
    }

    @Test
    fun aReloadKeepsEverythingItReturned() = runTest {
        // The guardrail of the previous rule: renewing is not erasing.
        cache.save(listOf(article(id = 1L)))
        val repository = repository(MockEngineResponse.Body(page(1L to "feed/1", 2L to "feed/2")))
        signedIn()

        repository.refresh()

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value }
        assertEquals(setOf(1L, 2L), cached.toSet())
    }

    /**
     * A read article whose mark has not left yet is spared: its row carries
     * the local "already read" memory, and losing it would bring the article
     * back as new on the server's next pass
     * (`ArticleDao.deleteReadCachedBefore`).
     */
    @Test
    fun aReloadSparesAReadArticleWhoseMarkHasNotLeftYet() = runTest {
        cache.save(listOf(article(id = 1L, title = "Lu hors ligne", isRead = true)))
        pendingMarks.enqueue(listOf(ArticleId(1L)))
        val repository = repository(MockEngineResponse.Body(page(2L to "feed/1")))
        signedIn()

        repository.refresh()

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value }
        assertTrue(1L in cached, "un marquage encore en file interdit la purge de sa ligne")
    }

    /**
     * Pagination purges nothing. Purging on each page would erase the feed
     * under the reader's eyes: articles read at the top would vanish while
     * the bottom loads.
     */
    @Test
    fun loadingTheNextPagePurgesNothing() = runTest {
        cache.save(listOf(article(id = 1L, title = "Lu", isRead = true)))
        val repository = repository(MockEngineResponse.Body(page(2L to "feed/1")))
        signedIn()

        repository.loadPage(cursor = PageCursor("45219"))

        val cached = repository.observeCachedArticles(CACHE_LIMIT).first().map { it.id.value }
        assertTrue(1L in cached, "seul un rechargement demandé renouvelle la liste (SPECS.md §4.6)")
    }

    @Test
    fun refreshDoesNotDisturbThePaginationContinuity() = runTest {
        // The reload retains nothing in the repository: continuity belongs
        // to the caller, which extends the traversal of its choice by
        // passing the matching tail, here the old traversal.
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
        val second = assertNotNull(
            repository.loadPage(first.nextCursor, previousTail = first.articles.takeLast(1)).valueOrNull(),
        )

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
        // Observed on a real instance: the server sorts its reading-list by
        // crawl date, and an article published two days earlier can open the
        // first page. Displayed as-is, the on-screen order would depend on
        // whether disk or network answers first at launch (the cache sorts
        // by publication), and reading resumption (SPECS.md §5.3), which
        // looks for the first article no newer, would land anywhere in a
        // non-chronological list. The page is therefore brought back to
        // publication order here, before the mix, which already expects it
        // (rule 2 of SPECS.md §4.2).
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
        // Equal dates break ties by descending id, the same tie-break as the
        // cache's SQL sort. Two different tie-breaks would again make the
        // displayed order depend on which source answered first.
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

    /** A page whose publication date is explicit, decoupled from the id. */
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
        // SPECS.md §4.6: this date is what says whether the displayed feed
        // is stale. An ordinary page counts as much as a refresh.
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
        // The server answered; the feed simply has nothing new. Recording
        // nothing would make a just-exhausted feed look stale.
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
        // Without this rule, an app left open offline all day would look
        // fresh.
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
