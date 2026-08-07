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
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncOutcome
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

/** Assez large pour lire toute la file d'un test, quelle que soit sa taille. */
private const val WHOLE_QUEUE = 1_000

/** Taille de lot attendue, telle que le dépôt la fixe. */
private const val BATCH_SIZE = 100

private const val FRESH_TOKEN = "JETON-FRAIS"

/** Délai de regroupement attendu, tel que le domaine le fixe par défaut. */
private const val GROUPING_DELAY_MILLIS = 5_000L

/**
 * Ce que ces tests éprouvent tient en une phrase : **la lecture ne dépend jamais
 * du réseau, et rien ne quitte la file sans confirmation du serveur** (SPECS.md
 * §4.5). Chaque échec est donc doublé d'une vérification de la file, seule
 * garantie que le marquage sera rejoué.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultReadSyncRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /**
     * Portée des transmissions différées, tenue à l'écart de [scope].
     *
     * DataStore y ferait vivre des coroutines sans fin, alors que les tests du
     * regroupement ont besoin d'attendre la fin des seules transmissions.
     */
    private val transmissionScope = CoroutineScope(dispatcher + SupervisorJob())

    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address

    // Base en mémoire : l'état lu local et la file se vérifient avec le vrai
    // moteur SQLite, seul capable de révéler une requête invalide.
    private val database = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val articleDao = database.articleDao()
    private val cache = ArticleCache(articleDao, Clock { 0L })
    private val queue = PendingMarkQueue(database.pendingMarkDao(), Clock { 0L })

    /** Réponses de `edit-tag`, consommées dans l'ordre ; `OK` une fois épuisées. */
    private val editTagReplies = ArrayDeque<Reply>()

    /** Réponses du point d'entrée `token`, même règle. */
    private val tokenReplies = ArrayDeque<Reply>()

    /** Formulaire de chaque `edit-tag` reçu : c'est lui qui porte les identifiants et le jeton. */
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
     * Un dépôt neuf sur la **même** base : c'est ce qui reproduit un
     * redémarrage de l'application, la file étant la seule mémoire commune.
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

    // ----- Marquage optimiste ------------------------------------------------

    @Test
    fun markingAnArticleChangesTheLocalStateWithoutTouchingTheNetwork() = runTest {
        // Le cœur de SPECS.md §4.5 : la lecture ne paie jamais le réseau. Aucun
        // appel ne doit partir, pas même l'obtention du jeton.
        cacheArticle(1L)

        repository.markAsRead(setOf(ArticleId(1L)))

        assertEquals(listOf(1L), articleDao.readArticleIds())
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
        // La file ne dépend pas du cache : un article purgé entre-temps doit
        // quand même être signalé au serveur.
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

        val outcome = repository.flush()

        assertEquals(ReadSyncOutcome.Synchronized(transmittedCount = 2), outcome)
        assertTrue(pendingIds().isEmpty())
        assertEquals(listOf("1", "2"), sentIds(request = 0))
    }

    @Test
    fun aFlushWithNothingPendingNeverTouchesTheNetwork() = runTest {
        // Payer un aller-retour pour n'envoyer aucun article serait absurde, et
        // c'est le cas courant du démarrage.
        signedIn()

        assertEquals(ReadSyncOutcome.Synchronized(transmittedCount = 0), repository.flush())
        assertEquals(0, tokenRequestCount)
        assertTrue(editTagForms.isEmpty())
    }

    @Test
    fun theQueueIsSentInBatchesRatherThanInOneRequest() = runTest {
        // Une file accumulée hors ligne ne doit pas partir en une requête
        // démesurée : au-delà de 1 000 champs, PHP les ignore en silence.
        signedIn()
        markAll(2 * BATCH_SIZE + 50)

        val outcome = repository.flush()

        assertEquals(ReadSyncOutcome.Synchronized(transmittedCount = 250), outcome)
        assertEquals(3, editTagForms.size)
        assertEquals(BATCH_SIZE, sentIds(request = 0).size)
        assertEquals(BATCH_SIZE, sentIds(request = 1).size)
        assertEquals(50, sentIds(request = 2).size)
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun theModificationTokenIsObtainedOnceForTheWholeFlush() = runTest {
        // Le jeton est déterministe et réutilisable : le redemander à chaque lot
        // doublerait le nombre d'allers-retours (docs/freshrss-api.md §2.3).
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

    // ----- Regroupement temporel (GOAL-008-T07) ------------------------------

    /*
     * Le **minutage** du regroupement se vérifie dans `:domain`
     * (ReadTransmissionSchedulerTest), où la transmission est un faux et le
     * temps entièrement virtuel. Ici, la transmission passe par Room, DataStore
     * et Ktor, qui suspendent réellement : `runTest` avance alors le temps
     * virtuel de lui-même pendant ces attentes, et la milliseconde n'y veut plus
     * rien dire. Ces tests-ci éprouvent donc le **câblage** — ce qui part, quand
     * on le déclenche, et ce qui reste en file — et rien d'autre.
     */

    /**
     * Amène la fenêtre à échéance, puis attend la fin de la transmission qu'elle
     * déclenche : le temps est virtuel, mais Ktor, lui, répond sur un vrai fil.
     */
    private suspend fun TestScope.awaitScheduledTransmission() {
        advanceTimeBy(GROUPING_DELAY_MILLIS)
        runCurrent()
        transmissionScope.coroutineContext.job.children.toList().forEach { it.join() }
    }

    @Test
    fun aMarkIsAppliedLocallyWithoutBeingTransmitted() = runTest(dispatcher) {
        // La moitié immédiate et la moitié différée dans le même test : au
        // retour de `markAsRead`, l'état local a basculé et la file est écrite,
        // alors que rien n'est encore parti.
        signedIn()
        cacheArticle(1L)

        repository.markAsRead(setOf(ArticleId(1L)))

        assertTrue(editTagForms.isEmpty())
        assertEquals(0, tokenRequestCount)
        assertEquals(listOf(1L), articleDao.readArticleIds())
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun aMarkIsTransmittedOnceTheGroupingDelayHasElapsed() = runTest(dispatcher) {
        // Sans `flush` : la fenêtre suffit à faire partir ce qui attend, sans
        // quoi le marquage attendrait le lancement suivant.
        signedIn()

        repository.markAsRead(setOf(ArticleId(1L)))
        awaitScheduledTransmission()

        assertEquals(listOf("1"), sentIds(request = 0))
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun aFlushTransmitsAndConsumesTheOpenWindow() = runTest(dispatcher) {
        // Le rejeu au démarrage ne peut pas attendre une fenêtre : `flush`
        // force. Et il consomme celle qui était ouverte, plutôt que d'en laisser
        // une partir à vide derrière lui.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))

        val outcome = repository.flush()

        assertEquals(ReadSyncOutcome.Synchronized(transmittedCount = 1), outcome)
        assertEquals(1, editTagForms.size)
        awaitScheduledTransmission()
        assertEquals(1, editTagForms.size)
    }

    @Test
    fun clearingPendingDropsTheScheduledTransmission() = runTest(dispatcher) {
        // Déconnexion pendant une fenêtre ouverte : la file est abandonnée, et
        // la transmission programmée n'a plus rien à dire au serveur.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.clearPending()
        awaitScheduledTransmission()

        assertTrue(editTagForms.isEmpty())
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun aMarkAddedAfterATransmissionGetsItsOwnWindow() = runTest(dispatcher) {
        // Rien ne se perd d'une fenêtre à l'autre : ce qui arrive après un envoi
        // ouvre le suivant, sans attendre un `flush`.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        awaitScheduledTransmission()

        repository.markAsRead(setOf(ArticleId(2L)))
        awaitScheduledTransmission()

        assertEquals(2, editTagForms.size)
        assertEquals(listOf("2"), sentIds(request = 1))
        assertTrue(pendingIds().isEmpty())
    }

    // ----- Échecs : la file survit -------------------------------------------

    @Test
    fun anUnreachableServerKeepsTheQueueIntact() = runTest {
        // Hors ligne : la transmission échoue, la file grossit, et rien ne se
        // voit à l'écran (SPECS.md §5.2). C'est le comportement voulu.
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        repository.markAsRead(setOf(ArticleId(1L)))
        val failing = failingRepository()

        val outcome = failing.flush()

        assertEquals(ReadSyncOutcome.Deferred(transmittedCount = 0), outcome)
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun aServerErrorKeepsTheQueueIntact() = runTest {
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Oups", HttpStatusCode.InternalServerError)

        val outcome = repository.flush()

        assertEquals(ReadSyncOutcome.Deferred(transmittedCount = 0), outcome)
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun anUnexpectedBodyKeepsTheQueueIntact() = runTest {
        // `edit-tag` répond `OK` en texte brut : autre chose signale un portail
        // captif ou une page de maintenance, pas un marquage effectué.
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("<html>maintenance</html>")

        assertEquals(ReadSyncOutcome.Deferred(transmittedCount = 0), repository.flush())
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun aFailureAfterAFirstBatchKeepsOnlyWhatDidNotGo() = runTest {
        signedIn(modificationToken = ModificationToken("JETON-CONNU"))
        markAll(BATCH_SIZE + 50)
        editTagReplies += Reply("OK")
        editTagReplies += Reply("Oups", HttpStatusCode.InternalServerError)

        val outcome = repository.flush()

        assertEquals(ReadSyncOutcome.Deferred(transmittedCount = BATCH_SIZE), outcome)
        assertEquals(50, pendingIds().size)
    }

    @Test
    fun flushingWithoutASessionKeepsTheQueueForTheNextSignIn() = runTest {
        repository.markAsRead(setOf(ArticleId(1L)))

        assertEquals(ReadSyncOutcome.SessionLost, repository.flush())
        assertEquals(listOf(1L), pendingIds())
        assertTrue(editTagForms.isEmpty())
    }

    // ----- Jeton refusé (GOAL-008-T05) ---------------------------------------

    @Test
    fun aRefusedModificationTokenIsRequestedAgainOnceAndTheBatchGoesThrough() = runTest {
        // Le jeton conservé peut avoir été invalidé côté serveur. Le redemander
        // est la conduite prescrite par docs/freshrss-api.md §2.3.
        signedIn(modificationToken = ModificationToken("PERIME"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        val outcome = repository.flush()

        assertEquals(ReadSyncOutcome.Synchronized(transmittedCount = 1), outcome)
        assertEquals(1, tokenRequestCount)
        assertEquals(listOf("PERIME", FRESH_TOKEN), editTagForms.map { it["T"] })
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun aSecondRefusalConcludesToALostSessionWithoutEmptyingTheQueue() = runTest {
        // Le point à ne pas manquer : les marquages doivent survivre à la
        // reconnexion, sinon l'utilisateur reverrait comme non lu ce qu'il a lu.
        signedIn(modificationToken = ModificationToken("PERIME"))
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)
        editTagReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        val outcome = repository.flush()

        assertEquals(ReadSyncOutcome.SessionLost, outcome)
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun theTokenIsNotAskedTwiceForTheSameBatch() = runTest {
        // Redemander en boucle ferait tourner l'application contre un serveur
        // qui a déjà tranché.
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
        // Le rappel de saisie survit : SPECS.md §3.4 ne fait rien retaper.
        assertEquals("alice", sessionStore.observeLastSignInHint().first()?.username)
    }

    @Test
    fun aRefusedTokenEndpointIsAlsoALostSession() = runTest {
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        tokenReplies += Reply("Unauthorized!", HttpStatusCode.Unauthorized)

        assertEquals(ReadSyncOutcome.SessionLost, repository.flush())
        assertEquals(listOf(1L), pendingIds())
    }

    @Test
    fun anUnavailableTokenEndpointOnlyDefersTheTransmission() = runTest {
        // Un `500` sur le jeton n'est pas un refus : la session est probablement
        // intacte, et déconnecter l'utilisateur serait une réaction excessive.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        tokenReplies += Reply("Oups", HttpStatusCode.InternalServerError)

        assertEquals(ReadSyncOutcome.Deferred(transmittedCount = 0), repository.flush())
        assertEquals(listOf(1L), pendingIds())
        assertNotNull(sessionStore.observeSession().first())
    }

    // ----- Rejeu et déconnexion ----------------------------------------------

    @Test
    fun aMarkingThatSurvivedARestartIsReplayed() = runTest {
        // SPECS.md §4.5 : « y compris après redémarrage de l'application ». La
        // file étant en base, un dépôt neuf retrouve ce que le précédent n'a pas
        // pu transmettre.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))
        editTagReplies += Reply("Oups", HttpStatusCode.InternalServerError)
        repository.flush()
        assertEquals(listOf(1L), pendingIds())

        val afterRestart = newRepository()
        val outcome = afterRestart.flush()

        assertEquals(ReadSyncOutcome.Synchronized(transmittedCount = 1), outcome)
        assertTrue(pendingIds().isEmpty())
    }

    @Test
    fun thePendingCountFollowsTheQueue() = runTest {
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L), ArticleId(2L)))
        assertEquals(2, repository.observePendingCount().first())

        repository.flush()

        assertEquals(0, repository.observePendingCount().first())
    }

    @Test
    fun clearingDropsWhatWasPendingWithoutTransmittingIt() = runTest {
        // Déconnexion (GOAL-008-T06) : ces marquages appartiennent au compte que
        // l'on vient de quitter, les envoyer sous une autre session marquerait
        // comme lus les articles de quelqu'un d'autre.
        signedIn()
        repository.markAsRead(setOf(ArticleId(1L)))

        repository.clearPending()

        assertTrue(pendingIds().isEmpty())
        assertTrue(editTagForms.isEmpty())
    }

    /** Un dépôt dont le réseau ne répond pas du tout — DNS, TLS, délai dépassé. */
    private fun failingRepository() = DefaultReadSyncRepository(
        api = FreshRssApi(createFreshRssHttpClient(MockEngine { throw IOException("hôte inconnu") })),
        sessionStore = sessionStore,
        articleCache = ArticleCache(articleDao, Clock { 0L }),
        queue = queue,
        ioDispatcher = dispatcher,
        applicationScope = transmissionScope,
    )
}
