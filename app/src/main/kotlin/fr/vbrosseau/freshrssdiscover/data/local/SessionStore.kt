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
import fr.vbrosseau.freshrssdiscover.domain.auth.SignInHint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the session across launches.
 *
 * The API password is never stored. The FreshRSS token does not expire
 * (docs/freshrss-api.md §2.1), so keeping it is enough to reopen the
 * application without signing in again; also keeping the password would add
 * nothing and double the exposed surface. When the token becomes invalid —
 * because the user changed their API password — SPECS.md §3.4 requires
 * returning to the sign-in screen, not retrying silently.
 *
 * Address and username are stored in clear text: they are not secrets, and
 * reading them helps diagnostics. Tokens go through [SecretCipher].
 */
@Singleton
internal class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    /**
     * Stored session, or `null`.
     *
     * `null` also covers a secret that became unreadable — keystore key lost
     * after a screen-lock change. From the application's point of view, there
     * is then no session: the only behavior that brings the user back to a
     * useful screen.
     */
    fun observeSession(): Flow<AuthSession?> = dataStore.data.map(::readSession).distinctUntilChanged()

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

    /**
     * Address and username of the last access, even without a valid session.
     *
     * Contains no secret: this is what allows keeping them after a rejected
     * token, whereas the tokens are wiped.
     */
    fun observeLastSignInHint(): Flow<SignInHint?> = dataStore.data.map(::readHint).distinctUntilChanged()

    /** Wipes the tokens, keeps the sign-in hint. */
    suspend fun invalidateTokens() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.AuthToken)
            preferences.remove(Keys.ModificationToken)
        }
    }

    /** Wipes everything, sign-in hint included. A deliberate user action. */
    suspend fun clear() {
        dataStore.edit { preferences ->
            Keys.all.forEach(preferences::remove)
        }
    }

    private fun readHint(preferences: Preferences): SignInHint? {
        val server = preferences[Keys.ServerBaseUrl]?.let(ServerAddress::parse) as? ServerAddressResult.Valid
        val username = preferences[Keys.Username]
        return if (server == null || username == null) null else SignInHint(server.address, username)
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
