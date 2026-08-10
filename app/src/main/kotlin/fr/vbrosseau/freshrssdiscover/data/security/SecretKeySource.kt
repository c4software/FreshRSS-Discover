package fr.vbrosseau.freshrssdiscover.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of the secret encryption key.
 *
 * This interface exists for one reason only: `AndroidKeyStore` is not
 * simulated by Robolectric, and still is not — retried on 2026-08-08, the
 * provider throws `NoSuchAlgorithmException`. As long as key provenance was
 * mixed with encryption, the whole class stayed out of reach of tests: the
 * format, GCM authentication, the handling of unreadable text.
 *
 * The split therefore happens here, at the smallest possible point. What
 * remains untested is [AndroidKeyStoreKeySource] and nothing else — about
 * twenty lines that only call the platform.
 */
internal fun interface SecretKeySource {
    fun key(): SecretKey
}

/**
 * The key lives in the hardware keystore and never leaves it.
 *
 * Even reading the preferences file — device unlocked by a third party,
 * extracted backup — does not yield the token.
 *
 * `androidx.security:security-crypto` would have done the same job, but the
 * library is deprecated: using it would violate AGENTS.md §2. The code below
 * only uses platform APIs that remain current.
 */
@Singleton
internal class AndroidKeyStoreKeySource @Inject constructor() : SecretKeySource {
    override fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        return existing ?: generate()
    }

    private fun generate(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately without `setUserAuthenticationRequired`: the
                // Discover feed must open without asking for an unlock at
                // every launch. The protected secret is an RSS reading token,
                // not a payment credential.
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "freshrss-discover.session"
    }
}
