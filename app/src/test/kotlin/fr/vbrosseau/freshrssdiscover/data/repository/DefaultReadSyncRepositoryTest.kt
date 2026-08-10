package fr.vbrosseau.freshrssdiscover.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.createFreshRssHttpClient
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.room.AppDatabase
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkQueue
import fr.vbrosseau.freshrssdiscover.data.security.FakeSecretCipher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.feed.Article
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedRef
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Large enough to read a test's whole queue, whatever its size. */
private const val WHOLE_QUEUE = 1_000

/** Expected batch size, as fixed by the repository. */
private const val BATCH_SIZE = 100

private const val FRESH_TOKEN = "JETON-FRAIS"

/** Expected grouping delay, as fixed by the domain default. */
private const val GROUPING_DELAY_MILLIS = 5_000L

/**
 * Reading never depends on the network, and nothing leaves the queue without
 * server confirmation (SPECS.md §4.5). Every failure test therefore also checks
 * the queue, the only guarantee that the mark will be replayed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultReadSyncRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /**
     * Scope for deferred transmissions, kept separate from [scope].
     *
     * DataStore would keep never-ending coroutines alive there, while the
     * grouping tests need to await the completion of transmissions only.
     */
    private val transmissionScope = CoroutineScope(dispatcher + SupervisorJob())

    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address

    // In-memory database: local read state and the queue are verified against
    // the real SQLite engine, the only thing able to reveal an invalid query.
    private val database = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val articleDao = database.articleDao()
    private val cache = ArticleCache(articleDao, Clock { 0L })
    private val queue = PendingMarkQueue(database.pendingMarkDao(), Clock { 0L })

    /** `edit-tag` replies, consumed in order; `OK` once exhausted. */
    private val editTagReplies = ArrayDeque<Reply>()

    /** `token` endpoint replies, same rule. */
    private val tokenReplies = ArrayDeque<Reply>()

    /** Form data of each received `edit-tag`: it carries the ids and the token. */
    private val editTagForms = mutableListOf<Parameters>()

    private var tokenRequestCount = 0

    private val engine = MockEngine { request ->
        if (request.url.encodedPath.endsWith("/reader/api/0/token")) {
            tokenRequestCount++
            reply(tokenReplies.removeFirstOrNull() ?: Reply(FRESH_TOKEN))
        } else {
            editTagForms += (request.body as FormDataContent).formData
            reply(editTagReplies.removeFirstOrNull() ?: Reply("OK"))
        }
    }

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: DefaultReadSyncRepository

    @Before
    fun buildRepository() {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("session.preferences_pb").also(File::delete)
        }
        sessionStore = SessionStore(dataStore, FakeSecretCipher())
        repository = newRepository()
    }

    @After
    fun releaseResources() {
        transmissionScope.cancel()
        scope.cancel()
        database.close()
    }

    /**
     * A fresh repository on the same database: reproduces an application
     * restart, the queue being the only shared memory.
     */
    private fun newRepository() = DefaultReadSyncRepository(
        api = FreshRssApi(createFreshRssHttpClient(engine)),
        sessionStore = sessionStore,
        articleCache = ArticleCache(articleDao, Clock { 0L }),
        queue = queue,
        ioDispatcher = dispatcher,
        applicationScope = transmissionScope,
    )

    private data class Reply(val body: String, val status: HttpStatusCode = HttpStatusCode.OK)

    private fun MockRequestHandleScope.reply(reply: Reply): HttpResponseData =
        respond(
            content = reply.body,
            status = reply.status,
            headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8"),
        )

    private suspend fun signedIn(modificationToken: ModificationToken? = null) {
        sessionStore.save(
            AuthSession(
                server = server,
                username = "alice",
                token = AuthToken("alice/c0ffee"),
                modificationToken = modificationToken,
            ),
        )
    }

    private suspend fun cacheArticle(id: Long) {
        cache.save(
            listOf(
                Article(
                    id = ArticleId(id),
                    title = "Un titre",
                    url = null,
                    publishedAtEpochSeconds = 1_700_000_000L,
                    summary = "Extrait",
                    imageUrl = null,
                    author = null,
                    feed = FeedRef(id = "feed/1", title = "Le flux"),
                    isRead = false,
                ),
            ),
        )
    }

    private suspend fun pendingIds(): List<Long> = queue.pending(WHOLE_QUEUE).map { it.value }

    private fun sentIds(request: Int): List<String> = editTagForms[request].getAll("i").orEmpty()

    private suspend fun markAll(count: Int) =
        repository.markAsRead((1..count).map { ArticleId(it.toLong()) }.toSet())

    // ----- Optimistic marking ------------------------------------------------

    @Test
    fun markingAnArticleChangesTheLocalStateWithoutTouchingTheNetwork() = runTest {
        // Core of SPECS.md §4.5: reading never pays for the network. No call
        // may go out, not even the token request.
        cacheArticle(1L)

        repository.markAsRead(setOf(ArticleId(1L)))

        assertEquals(listOf(1L), articleDao.readArticleIdsAmong(listOf(1L)))
        assertTrue(editTagForms.isEmpty())
        assertEquals(0, tokenRequestCount)
    }

    @Test
    fun markingAnArticleQueuesItForTransmission() = runTest {
        repository.markAsRead(setOf(ArticleId(1L), ArticleId(2L)))

        assertEquals(listOf(1L, 2L), pendingIds())
    }

    @Test
    fun markingAnArticleAbsentFromTheCacheStillQueuesIt() = runTest {
        // The queue does not depend on the cache: an article purged in the
        // meantime must still be reported to the server.
        repository.markAsRead(setOf(ArticleId(9L)))

        assertEquals(listOf(9L), pendingIds())
    }

    @Test
    fun markingNothingWritesNothing() = runTest {
        repository.markAsRead(emptySet())

        assertTrue(pendingIds().isEmpty())
    }

    // ----- Transmission ------------------------------------------------------

    @Test
    fun aConfirmedBatchLeavesTheQueueEmpty() = runTest {
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L), ArticleId(2L)))

        repository.flush()

        assertTrue(pendingIds().isEmpty())
        assertEquals(listOf("1", "2"), sentIds(request = 0))
    }

    @Test
    fun aFlushWithNothingPendingNeverTouchesTheNetwork() = runTest {
        // Paying a round trip to send no article would be pointless, and this
        // is the common case at startup.
        signedIn()

        repository.flush()

        assertEquals(0, tokenRequestCount)
        assertTrue(editTagForms.isEmpty())
    }

    @Test
    fun theQueueIsSentInBatchesRatherThanInOneRequest() = runTest {
        // A queue accumulated offline must not leave in one oversized request:
        // beyond 1,000 fields, PHP silently drops them.
        signedIn()
        markAll(2 * BATCH_SIZE + 50)

        repository.flush()

        assertEquals(3, editTagForms.size)
        assertEquals(BATCH_SIZE, sentIds(request = 0).size)
        assertEquals(BATCH_SIZE, sentIds(request = 1).size)
        assertEquals(50, sentIds(request = 2).size)
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun theModificationTokenIsObtainedOnceForTheWholeFlush() = runTest {
        // The token is deterministic and reusable: requesting it again for each
        // batch would double the number of round trips (docs/freshrss-api.md §2.3).
        signedIn()
        markAll(BATCH_SIZE + 1)

        repository.flush()

        assertEquals(1, tokenRequestCount)
        assertEquals(FRESH_TOKEN, editTagForms.first()["T"])
    }

    @Test
    fun theModificationTokenIsKeptWithTheSessionForTheNextLaunch() = runTest {
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.flush()

        val session = assertNotNull(sessionStore.observeSession().first())
        assertEquals(ModificationToken(FRESH_TOKEN), session.modificationToken)
    }

    @Test
    fun aKnownModificationTokenSpareTheExtraRoundTrip() = runTest {
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.flush()

        assertEquals(0, tokenRequestCount)
        assertEquals("JETON-CONNU", editTagForms.single()["T"])
    }

    // ----- Time-based grouping (GOAL-008-T07) --------------------------------

    /*
     * The grouping *timing* is verified in `:domain` (ReadTransmissionSchedulerTest),
     * where the transmission is a fake and time fully virtual. Here the
     * transmission goes through Room, DataStore and Ktor, which really suspend:
     * `runTest` then advances virtual time on its own during those waits, so
     * millisecond precision is meaningless. These tests therefore verify the
     * wiring only: what goes out when triggered, and what stays queued.
     */

    /**
     * Advances the window to expiry, then awaits the transmission it triggers:
     * time is virtual, but Ktor answers on a real thread.
     */
    private suspend fun TestScope.awaitScheduledTransmission() {
        advanceTimeBy(GROUPING_DELAY_MILLIS)
        runCurrent()
        transmissionScope.coroutineContext.job.children.toList().forEach { it.join() }
    }

    @Test
    fun aMarkIsAppliedLocallyWithoutBeingTransmitted() = runTest(dispatcher) {
        // When `markAsRead` returns, the local state has flipped and the queue
        // is written, while nothing has been transmitted yet.
        signedIn()
        cacheArticle(1L)

        repository.markAsRead(setOf(ArticleId(1L)))

        assertTrue(editTagForms.isEmpty())
        assertEquals(0, tokenRequestCount)
        assertEquals(listOf(1L), articleDao.readArticleIdsAmong(listOf(1L)))
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun aMarkIsTransmittedOnceTheGroupingDelayHasElapsed() = runTest(dispatcher) {
        // No `flush`: the window alone must send what is waiting, otherwise the
        // mark would wait until the next launch.
        signedIn()

        repository.markAsRead(setOf(ArticleId(1L)))
        awaitScheduledTransmission()

        assertEquals(listOf("1"), sentIds(request = 0))
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun aFlushTransmitsAndConsumesTheOpenWindow() = runTest(dispatcher) {
        // Replay at startup cannot wait for a window: `flush` forces it, and
        // consumes the open window rather than letting one fire empty behind it.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.flush()

        assertEquals(1, editTagForms.size)
        awaitScheduledTransmission()
        assertEquals(1, editTagForms.size)
    }

    @Test
    fun clearingPendingDropsTheScheduledTransmission() = runTest(dispatcher) {
        // Sign-out during an open window: the queue is dropped and the
        // scheduled transmission has nothing left to tell the server.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.clearPending()
        awaitScheduledTransmission()

        assertTrue(editTagForms.isEmpty())
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun aMarkAddedAfterATransmissionGetsItsOwnWindow() = runTest(dispatcher) {
        // Nothing is lost between windows: a mark arriving after a transmission
        // opens the next one, without waiting for a `flush`.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        awaitScheduledTransmission()

        repository.markAsRead(setOf(ArticleId(2L)))
        awaitScheduledTransmission()

        assertEquals(2, editTagForms.size)
        assertEquals(listOf("2"), sentIds(request = 1))
        assertTrue(pendingIds().isEmpty())
    }

    // ----- Failures: the queue survives --------------------------------------

    @Test
    fun anUnreachableServerKeepsTheQueueIntact() = runTest {
        // Offline: the transmission fails, the queue grows, and nothing shows
        // on screen (SPECS.md §5.2). This is the intended behavior.
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        repository.markAsRead(setOf(ArticleId(1L)))
        val failing = failingRepository()

        failing.flush()

        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun aServerErrorKeepsTheQueueIntact() = runTest {
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Oups", HttpStatusCode.InternalServerError)

        repository.flush()

        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun anUnexpectedBodyKeepsTheQueueIntact() = runTest {
        // `edit-tag` answers `OK` in plain text: anything else signals a
        // captive portal or a maintenance page, not a successful mark.
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("<html>maintenance</html>")

        repository.flush()

        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun aFailureAfterAFirstBatchKeepsOnlyWhatDidNotGo() = runTest {
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        markAll(BATCH_SIZE + 50)
        editTagReplies += Reply("OK")
        editTagReplies += Reply("Oups", HttpStatusCode.InternalServerError)

        repository.flush()

        // The first batch went out and was acknowledged; only the second remains.
        assertEquals(2, editTagForms.size)
        assertEquals(50, pendingIds().size)
    }

    @Test
    fun flushingWithoutASessionKeepsTheQueueForTheNextSignIn() = runTest {
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.flush()

        assertEquals(listOf(1L), pendingIds())
        assertTrue(editTagForms.isEmpty())
        assertEquals(0, tokenRequestCount)
    }

    // ----- Refused token (GOAL-008-T05) --------------------------------------

    @Test
    fun aRefusedModificationTokenIsRequestedAgainOnceAndTheBatchGoesThrough() = runTest {
        // The stored token may have been invalidated server-side. Requesting a
        // new one is the behavior prescribed by docs/freshrss-api.md §2.3.
        signedIn(modificationToken = ModificationToken("PERIME"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        repository.flush()

        assertEquals(1, tokenRequestCount)
        assertEquals(listOf("PERIME", FRESH_TOKEN), editTagForms.map { it["T"] })
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun aSecondRefusalConcludesToALostSessionWithoutEmptyingTheQueue() = runTest {
        // Marks must survive the re-sign-in, otherwise the user would see as
        // unread what they already read.
        signedIn(modificationToken = ModificationToken("PERIME"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)
        editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        repository.flush()

        // The session is gone (observed by the next test) but the queue is intact.
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun theTokenIsNotAskedTwiceForTheSameBatch() = runTest {
        // Requesting the token in a loop would spin the app against a server
        // that has already made its decision.
        signedIn(modificationToken = ModificationToken("PERIME"))
        repository.markAsRead(setOf(ArticleId(1L)))
        repeat(2) { editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized) }

        repository.flush()

        assertEquals(1, tokenRequestCount)
        assertEquals(2, editTagForms.size)
    }

    @Test
    fun aLostSessionEndsTheSessionSoTheRootRedirectsByItself() = runTest {
        signedIn(modificationToken = ModificationToken("PERIME"))
        repository.markAsRead(setOf(ArticleId(1L)))
        repeat(2) { editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized) }

        repository.flush()

        assertNull(sessionStore.observeSession().first())
        // The sign-in hint survives: SPECS.md §3.4 makes the user retype nothing.
        assertEquals("alice", sessionStore.observeLastSignInHint().first()?.username)
    }

    @Test
    fun aRefusedTokenEndpointIsAlsoALostSession() = runTest {
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        tokenReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        repository.flush()

        assertNull(sessionStore.observeSession().first())
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun anUnavailableTokenEndpointOnlyDefersTheTransmission() = runTest {
        // A `500` on the token endpoint is not a refusal: the session is
        // probably intact, and signing the user out would be an overreaction.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        tokenReplies += Reply("Oups", HttpStatusCode.InternalServerError)

        repository.flush()

        assertEquals(listOf(1L), pendingIds())
        assertNotNull(sessionStore.observeSession().first())
    }

    // ----- Replay and sign-out -----------------------------------------------

    @Test
    fun aMarkingThatSurvivedARestartIsReplayed() = runTest {
        // SPECS.md §4.5: "including after an application restart". The queue
        // lives in the database, so a fresh repository recovers what the
        // previous one could not transmit.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Oups", HttpStatusCode.InternalServerError)
        repository.flush()
        assertEquals(listOf(1L), pendingIds())

        val afterRestart = newRepository()
        afterRestart.flush()

        assertEquals(listOf("1"), sentIds(request = 1))
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun clearingDropsWhatWasPendingWithoutTransmittingIt() = runTest {
        // Sign-out (GOAL-008-T06): these marks belong to the account just left;
        // sending them under another session would mark someone else's articles
        // as read.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.clearPending()

        assertTrue(pendingIds().isEmpty())
        assertTrue(editTagForms.isEmpty())
    }

    /** A repository whose network does not answer at all: DNS, TLS, timeout. */
    private fun failingRepository() = DefaultReadSyncRepository(
        api = FreshRssApi(createFreshRssHttpClient(MockEngine { throw IOException("hôte inconnu") })),
        sessionStore = sessionStore,
        articleCache = ArticleCache(articleDao, Clock { 0L }),
        queue = queue,
        ioDispatcher = dispatcher,
        applicationScope = transmissionScope,
    )
}
