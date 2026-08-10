package fr.vbrosseau.freshrssdiscover.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.local.FeedFreshnessStore
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.SettingsStore
import fr.vbrosseau.freshrssdiscover.data.local.room.AppDatabase
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleDao
import fr.vbrosseau.freshrssdiscover.data.local.room.CacheMaintenance
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkDao
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkQueue
import fr.vbrosseau.freshrssdiscover.data.network.AndroidNetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultArticleRepository
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultAuthRepository
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultReadSyncRepository
import fr.vbrosseau.freshrssdiscover.data.security.KeystoreSecretCipher
import fr.vbrosseau.freshrssdiscover.data.security.SecretCipher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.SessionGate
import fr.vbrosseau.freshrssdiscover.presentation.SessionGateViewModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginViewModel
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsViewModel
import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Provider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Current `AppDatabase` version, as described by `app/schemas/…/2.json`. */
private const val EXPECTED_DATABASE_VERSION = 2

/**
 * Exercises the complete construction of the injection graph.
 *
 * Hilt checks a lot at compile time: a missing binding does not compile. It
 * checks nothing about instantiation: a throwing `@Provides`, a database that
 * fails to open, a cycle that only closes at runtime. This test therefore
 * actually requests every piece of the graph.
 *
 * It also verifies which implementation each `@Binds` points to, the
 * counterpart of the qualifier inversion covered by [DispatcherModuleTest]:
 * binding `AuthRepository` to the wrong class compiles fine, and only a
 * runtime check can see it.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var articleRepository: ArticleRepository

    @Inject
    lateinit var freshnessRepository: FeedFreshnessRepository

    @Inject
    lateinit var readSyncRepository: ReadSyncRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var cacheRepository: CacheRepository

    @Inject
    internal lateinit var networkAvailability: NetworkAvailability

    @Inject
    internal lateinit var database: AppDatabase

    @Inject
    internal lateinit var databaseProvider: Provider<AppDatabase>

    @Inject
    internal lateinit var articleDao: ArticleDao

    @Inject
    internal lateinit var pendingMarkDao: PendingMarkDao

    @Inject
    internal lateinit var articleCache: ArticleCache

    @Inject
    internal lateinit var articleCacheProvider: Provider<ArticleCache>

    @Inject
    internal lateinit var pendingMarkQueue: PendingMarkQueue

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    internal lateinit var sessionStore: SessionStore

    @Inject
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var httpClientProvider: Provider<HttpClient>

    @Inject
    internal lateinit var api: FreshRssApi

    @Inject
    internal lateinit var secretCipher: SecretCipher

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    /**
     * `WorkManager` must be initialized before injection.
     *
     * The graph provides the application's `WorkManager`, which does not exist
     * under `HiltTestApplication`: without this bootstrap, injection fails
     * before any ViewModel is built and the real scheduler would have to be
     * replaced by a fake. This test only has value if everything comes from
     * the real graph.
     */
    @Before
    fun injectDependencies() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        hiltRule.inject()
    }

    /**
     * The database comes from the graph, hence a real file: closing it avoids
     * leaking an open SQLite connection from one test to the next.
     */
    @After
    fun closeDatabase() {
        database.close()
    }

    // ----- Resolution ---------------------------------------------------------

    @Test
    fun everyRepositoryResolvesToItsProductionImplementation() {
        assertIs<DefaultAuthRepository>(authRepository)
        assertIs<DefaultArticleRepository>(articleRepository)
        assertIs<FeedFreshnessStore>(freshnessRepository)
        assertIs<DefaultReadSyncRepository>(readSyncRepository)
        assertIs<SettingsStore>(settingsRepository)
        assertIs<CacheMaintenance>(cacheRepository)
        assertIs<AndroidNetworkAvailability>(networkAvailability)
    }

    @Test
    fun theSecretCipherResolvesToTheKeystoreImplementation() {
        // The only component tests cannot exercise, since Robolectric does not
        // simulate `AndroidKeyStore`. Its construction by the graph is
        // verifiable, and would fail if the `@Binds` ever pointed to a test
        // implementation.
        assertIs<KeystoreSecretCipher>(secretCipher)
    }

    @Test
    fun everyPersistenceComponentIsBuiltByTheGraph() = runTest {
        // Room, DataStore and their wrappers are requested together and
        // actually queried: each only runs its opening code on first access,
        // which neither the Hilt processor nor merely providing the object
        // triggers.
        assertEquals(EXPECTED_DATABASE_VERSION, database.openHelper.writableDatabase.version)
        assertTrue(articleDao.readArticleIdsAmong(listOf(1L)).isEmpty())
        assertTrue(pendingMarkDao.pending(limit = 1).isEmpty())
        assertTrue(articleCache.observeArticles(limit = 1).first().isEmpty())
        assertTrue(pendingMarkQueue.pending(limit = 1).isEmpty())
        assertNull(sessionStore.observeSession().first())
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun theHttpClientAndTheApiAreBuiltByTheGraph() {
        // `createFreshRssHttpClient` installs content negotiation and logging;
        // an invalid configuration only shows up here.
        assertTrue(httpClient.isActive)
        assertIs<FreshRssApi>(api)
    }

    @Test
    fun theApplicationScopeIsAliveAndTheClockAdvances() {
        assertTrue(applicationScope.isActive)
        assertTrue(clock.nowEpochMillis() > 0L)
    }

    // ----- Scopes -------------------------------------------------------------

    @Test
    fun singletonBindingsAreSharedRatherThanRebuilt() {
        // A forgotten scope compiles: it just produces two databases, two HTTP
        // clients, two caches, and two truths where the application assumes one.
        assertSame(database, databaseProvider.get())
        assertSame(httpClient, httpClientProvider.get())
        assertSame(articleCache, articleCacheProvider.get())
    }

    // ----- ViewModels ---------------------------------------------------------

    @Test
    fun everyViewModelIsBuiltFromRealGraphDependencies() {
        // Hilt forbids injecting a `@HiltViewModel` directly: their graph lives
        // in a screen-scoped component and the compiler rejects the request.
        // What can be established without starting an Activity is that their
        // dependencies all come from the real graph and suffice to build them;
        // a throwing `init` would surface here.
        val sessionGate = SessionGateViewModel(authRepository)
        val login = LoginViewModel(authRepository)
        val discover = DiscoverViewModel(
            articleRepository = articleRepository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            freshnessRepository = freshnessRepository,
            clock = clock,
        )
        val settings = SettingsViewModel(
            authRepository,
            settingsRepository,
            cacheRepository,
            reminderScheduler,
        )
        try {
            assertEquals(SessionGate.Unknown, sessionGate.gate.value)
            assertFalse(login.uiState.value.isSubmitting)
            assertEquals(DiscoverPhase.InitialLoading, discover.uiState.value.phase)
            assertFalse(settings.uiState.value.isSignOutConfirmationVisible)
        } finally {
            // Jobs launched in an `init` would otherwise outlive the test and
            // land on a torn-down Robolectric environment, the trap described
            // by `TestApplication`.
            listOf(sessionGate, login, discover, settings).forEach(ViewModel::cancelScope)
        }
    }
}

private fun ViewModel.cancelScope() {
    viewModelScope.cancel()
}
