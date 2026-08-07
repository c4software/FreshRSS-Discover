package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.data.security.SecretCipher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conserve la session entre deux lancements.
 *
 * **Le mot de passe API n'est jamais enregistré.** Le jeton FreshRSS n'expirant
 * pas (docs/freshrss-api.md §2.1), le conserver suffit à rouvrir l'application
 * sans reconnexion ; garder en plus le mot de passe n'apporterait rien et
 * doublerait la surface exposée. Lorsque le jeton devient invalide — parce que
 * l'utilisateur a changé son mot de passe API — SPECS.md §3.4 demande de
 * revenir à l'écran de connexion, pas de retenter en silence.
 *
 * Adresse et identifiant sont en clair : ce ne sont pas des secrets, et les
 * lire aide à diagnostiquer. Les jetons, eux, passent par [SecretCipher].
 */
@Singleton
internal class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    /**
     * Session enregistrée, ou `null`.
     *
     * `null` couvre aussi le cas d'un secret devenu illisible — clé du
     * *keystore* perdue après un changement de verrouillage d'écran. Du point
     * de vue de l'application, il n'y a alors pas de session : c'est la seule
     * conduite qui ramène l'utilisateur à un écran utile.
     */
    fun observeSession(): Flow<AuthSession?> = dataStore.data.map(::readSession)

    suspend fun save(session: AuthSession) {
        dataStore.edit { preferences ->
            preferences[Keys.ServerBaseUrl] = session.server.baseUrl
            preferences[Keys.Username] = session.username
            preferences[Keys.AuthToken] = cipher.encrypt(session.token.value)
            val modificationToken = session.modificationToken
            if (modificationToken == null) {
                preferences.remove(Keys.ModificationToken)
            } else {
                preferences[Keys.ModificationToken] = cipher.encrypt(modificationToken.value)
            }
        }
    }

    /** Efface tout. Appelée à la déconnexion et lorsque le serveur refuse le jeton. */
    suspend fun clear() {
        dataStore.edit { preferences ->
            Keys.all.forEach(preferences::remove)
        }
    }

    private fun readSession(preferences: Preferences): AuthSession? {
        val baseUrl = preferences[Keys.ServerBaseUrl]
        val username = preferences[Keys.Username]
        val token = preferences[Keys.AuthToken]?.let(cipher::decrypt)
        val server = baseUrl?.let(ServerAddress::parse) as? ServerAddressResult.Valid

        return if (server == null || username == null || token.isNullOrEmpty()) {
            null
        } else {
            AuthSession(
                server = server.address,
                username = username,
                token = AuthToken(token),
                modificationToken = preferences[Keys.ModificationToken]
                    ?.let(cipher::decrypt)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::ModificationToken),
            )
        }
    }

    private object Keys {
        val ServerBaseUrl = stringPreferencesKey("session.server_base_url")
        val Username = stringPreferencesKey("session.username")
        val AuthToken = stringPreferencesKey("session.auth_token")
        val ModificationToken = stringPreferencesKey("session.modification_token")

        val all = listOf(ServerBaseUrl, Username, AuthToken, ModificationToken)
    }
}
