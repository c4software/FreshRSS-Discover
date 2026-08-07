package fr.vbrosseau.freshrssdiscover.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
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
import fr.vbrosseau.freshrssdiscover.domain.feed.ReadingPositionRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.presentation.SessionGate
import fr.vbrosseau.freshrssdiscover.presentation.SessionGateViewModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverPhase
import fr.vbrosseau.freshrssdiscover.presentation.discover.DiscoverViewModel
import fr.vbrosseau.freshrssdiscover.presentation.discover.ReadingPositionViewModel
import fr.vbrosseau.freshrssdiscover.presentation.login.LoginViewModel
import fr.vbrosseau.freshrssdiscover.presentation.settings.SettingsViewModel
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

/** La version courante d'`AppDatabase`, telle que `app/schemas/…/2.json` la décrit. */
private const val EXPECTED_DATABASE_VERSION = 2

/**
 * Éprouve la construction **complète** du graphe d'injection.
 *
 * Hilt vérifie beaucoup à la compilation : un binding manquant ne compile pas.
 * Il ne vérifie rien de ce qui arrive à l'**instanciation** — un `@Provides`
 * qui lève, une base qu'on n'arrive pas à ouvrir, un cycle qui ne se referme
 * qu'à l'exécution. Or, jusqu'ici, aucun test ne demandait au graphe autre
 * chose que trois dispatchers ([DispatcherModuleTest]) : le reste n'était
 * fabriqué que par l'application réelle, chez l'utilisateur.
 *
 * Ce test demande donc effectivement chaque pièce du graphe.
 *
 * Il vérifie de plus **vers quelle implémentation** chaque `@Binds` pointe.
 * C'est le pendant de l'inversion de qualifiers que couvre
 * [DispatcherModuleTest] : lier `AuthRepository` à la mauvaise classe compile
 * parfaitement, seule une vérification à l'exécution le voit.
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
    lateinit var readingPositionRepository: ReadingPositionRepository

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
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    /**
     * La base vient du graphe, donc d'un vrai fichier : la refermer évite de
     * laisser une connexion SQLite ouverte d'un test à l'autre.
     */
    @After
    fun closeDatabase() {
        database.close()
    }

    // ----- Résolution ---------------------------------------------------------

    @Test
    fun everyRepositoryResolvesToItsProductionImplementation() {
        assertIs<DefaultAuthRepository>(authRepository)
        assertIs<DefaultArticleRepository>(articleRepository)
        assertIs<DefaultReadSyncRepository>(readSyncRepository)
        assertIs<SettingsStore>(settingsRepository)
        assertIs<CacheMaintenance>(cacheRepository)
        assertIs<AndroidNetworkAvailability>(networkAvailability)
    }

    @Test
    fun theSecretCipherResolvesToTheKeystoreImplementation() {
        // Le seul composant que les tests ne peuvent pas exercer — Robolectric
        // ne simule pas `AndroidKeyStore`. Sa *construction* par le graphe, en
        // revanche, se vérifie : c'est elle qui échouerait si le `@Binds`
        // désignait un jour une implémentation de test.
        assertIs<KeystoreSecretCipher>(secretCipher)
    }

    @Test
    fun everyPersistenceComponentIsBuiltByTheGraph() = runTest {
        // Room, DataStore et leurs enveloppes sont demandés ensemble, et
        // réellement interrogés : chacun n'exécute son code d'ouverture qu'au
        // premier accès, ce que ni le processeur Hilt ni la simple fourniture
        // de l'objet ne déclenchent.
        assertEquals(EXPECTED_DATABASE_VERSION, database.openHelper.writableDatabase.version)
        assertTrue(articleDao.readArticleIds().isEmpty())
        assertTrue(pendingMarkDao.pending(limit = 1).isEmpty())
        assertTrue(articleCache.observeArticles(limit = 1).first().isEmpty())
        assertTrue(pendingMarkQueue.pending(limit = 1).isEmpty())
        assertNull(sessionStore.observeSession().first())
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun theHttpClientAndTheApiAreBuiltByTheGraph() {
        // `createFreshRssHttpClient` installe négociation de contenu et
        // journalisation ; une configuration invalide ne se voit qu'ici.
        assertTrue(httpClient.isActive)
        assertIs<FreshRssApi>(api)
    }

    @Test
    fun theApplicationScopeIsAliveAndTheClockAdvances() {
        assertTrue(applicationScope.isActive)
        assertTrue(clock.nowEpochMillis() > 0L)
    }

    // ----- Portées ------------------------------------------------------------

    @Test
    fun singletonBindingsAreSharedRatherThanRebuilt() {
        // Une portée oubliée compile : elle produit seulement deux bases, deux
        // clients HTTP, deux caches — et deux vérités là où l'application en
        // suppose une.
        assertSame(database, databaseProvider.get())
        assertSame(httpClient, httpClientProvider.get())
        assertSame(articleCache, articleCacheProvider.get())
    }

    // ----- ViewModels ---------------------------------------------------------

    @Test
    fun everyViewModelIsBuiltFromRealGraphDependencies() {
        // Hilt **interdit** d'injecter directement une `@HiltViewModel` : leur
        // graphe vit dans un composant propre à l'écran, et le compilateur
        // rejette la demande. Ce qui s'établit sans démarrer d'Activity, c'est
        // que leurs dépendances viennent toutes du graphe réel et suffisent à
        // les construire — un `init` qui lèverait apparaîtrait ici.
        val sessionGate = SessionGateViewModel(authRepository)
        val login = LoginViewModel(authRepository)
        val readingPosition = ReadingPositionViewModel(readingPositionRepository)
        val discover = DiscoverViewModel(
            articleRepository = articleRepository,
            readSyncRepository = readSyncRepository,
            settingsRepository = settingsRepository,
            clock = clock,
        )
        val settings = SettingsViewModel(authRepository, settingsRepository, cacheRepository)
        try {
            assertEquals(SessionGate.Unknown, sessionGate.gate.value)
            assertFalse(login.uiState.value.isSubmitting)
            assertNull(readingPosition.restoreToArticleId.value)
            assertEquals(DiscoverPhase.InitialLoading, discover.uiState.value.phase)
            assertFalse(settings.uiState.value.isSignOutConfirmationVisible)
        } finally {
            // Les travaux lancés dans un `init` vivraient sinon plus longtemps
            // que le test et retomberaient sur un environnement Robolectric
            // démonté — le piège décrit par `TestApplication`.
            listOf(sessionGate, login, discover, settings).forEach(ViewModel::cancelScope)
        }
    }
}

private fun ViewModel.cancelScope() {
    viewModelScope.cancel()
}
