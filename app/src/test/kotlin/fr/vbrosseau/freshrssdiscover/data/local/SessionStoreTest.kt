package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.data.security.FakeSecretCipher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = FakeSecretCipher()
    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val session = AuthSession(
        server = server,
        username = "alice",
        token = AuthToken("alice/c0ffee"),
    )

    private lateinit var dataStore: DataStore<Preferences>

    private fun store(): SessionStore {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("session.preferences_pb").also(java.io.File::delete)
        }
        return SessionStore(dataStore, cipher)
    }

    @After
    fun stopWriting() {
        scope.cancel()
    }

    @Test
    fun noSessionIsStoredInitially() = runTest {
        assertNull(store().observeSession().first())
    }

    @Test
    fun aSavedSessionIsReadBack() = runTest {
        val store = store()
        store.save(session)

        val restored = assertNotNull(store.observeSession().first())
        assertEquals(server, restored.server)
        assertEquals("alice", restored.username)
        assertEquals("alice/c0ffee", restored.token.value)
    }

    @Test
    fun theTokenIsNeverWrittenInClear() = runTest {
        // Le cœur du sujet : une écriture en clair rendrait le jeton lisible
        // par toute lecture du fichier de préférences.
        val store = store()
        store.save(session)

        val raw = dataStore.data.first().asMap().values.map(Any::toString)
        assertFalse(raw.any { "alice/c0ffee" in it }, "le jeton apparaît en clair dans $raw")
        assertTrue(raw.any { it.startsWith(FakeSecretCipher.PREFIX) })
    }

    @Test
    fun theServerAndUsernameStayReadable() = runTest {
        // Ce ne sont pas des secrets, et les lire aide à diagnostiquer.
        val store = store()
        store.save(session)

        val raw = dataStore.data.first().asMap().values.map(Any::toString)
        assertTrue(raw.any { "https://exemple.org" in it })
        assertTrue(raw.any { "alice" == it })
    }

    @Test
    fun theModificationTokenIsAlsoEncryptedAndRestored() = runTest {
        val store = store()
        store.save(session.copy(modificationToken = ModificationToken("ZZZ-jeton")))

        val restored = assertNotNull(store.observeSession().first())
        assertEquals("ZZZ-jeton", restored.modificationToken?.value)

        val raw = dataStore.data.first().asMap().values.map(Any::toString)
        assertFalse(raw.any { "ZZZ-jeton" in it })
    }

    @Test
    fun savingWithoutAModificationTokenRemovesAPreviousOne() = runTest {
        // Sans cela, un jeton de modification périmé survivrait à une
        // reconnexion et ferait échouer le premier marquage comme lu.
        val store = store()
        store.save(session.copy(modificationToken = ModificationToken("ancien")))
        store.save(session)

        assertNull(assertNotNull(store.observeSession().first()).modificationToken)
    }

    @Test
    fun clearingRemovesEverything() = runTest {
        val store = store()
        store.save(session.copy(modificationToken = ModificationToken("ZZZ")))

        store.clear()

        assertNull(store.observeSession().first())
        assertTrue(dataStore.data.first().asMap().isEmpty(), "des clés subsistent après la déconnexion")
    }

    @Test
    fun invalidatingTokensKeepsTheSignInHint() = runTest {
        // SPECS.md §3.4 : ramener à l'écran de connexion sans faire tout
        // retaper. L'utilisateur n'a probablement qu'un mot de passe API à
        // renouveler.
        val store = store()
        store.save(session)

        store.invalidateTokens()

        assertNull(store.observeSession().first())
        val hint = assertNotNull(store.observeLastSignInHint().first())
        assertEquals("https://exemple.org", hint.server.baseUrl)
        assertEquals("alice", hint.username)
    }

    @Test
    fun signingOutErasesTheSignInHintToo() = runTest {
        // Geste délibéré de l'utilisateur : il n'a aucune raison de laisser
        // une trace de son serveur sur l'appareil.
        val store = store()
        store.save(session)

        store.clear()

        assertNull(store.observeLastSignInHint().first())
    }

    @Test
    fun thereIsNoHintBeforeAnyConnection() = runTest {
        assertNull(store().observeLastSignInHint().first())
    }

    @Test
    fun aLostKeystoreKeyStillLeavesTheSignInHint() = runTest {
        // Le rappel de saisie ne passe pas par le chiffreur : une clé perdue
        // ne doit pas obliger à retaper l'adresse.
        val store = store()
        store.save(session)
        cipher.keyIsLost = true

        assertNotNull(store.observeLastSignInHint().first())
    }

    @Test
    fun aLostKeystoreKeyIsReportedAsNoSessionRatherThanCrashing() = runTest {
        // Cas réel : changement de verrouillage d'écran, ou restauration d'une
        // sauvegarde sur un autre appareil. Le secret devient illisible ; la
        // seule conduite utile est de ramener à l'écran de connexion.
        val store = store()
        store.save(session)

        cipher.keyIsLost = true

        assertNull(store.observeSession().first())
    }

    @Test
    fun aSessionWhoseServerBecameUnparsableIsIgnored() = runTest {
        // Défend contre une donnée corrompue ou écrite par une version
        // antérieure : mieux vaut redemander la connexion que propager une
        // adresse inexploitable dans toute l'application.
        val store = store()
        store.save(session)
        dataStore.edit { it[stringPreferencesKey("session.server_base_url")] = "" }

        assertNull(store.observeSession().first())
    }
}
