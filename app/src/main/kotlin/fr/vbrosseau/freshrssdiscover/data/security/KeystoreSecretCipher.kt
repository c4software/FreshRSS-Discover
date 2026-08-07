package fr.vbrosseau.freshrssdiscover.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chiffrement AES/GCM adossé à `AndroidKeyStore`.
 *
 * La clé ne quitte jamais le magasin matériel : même une lecture du fichier de
 * préférences — appareil déverrouillé par un tiers, sauvegarde extraite — ne
 * livre pas le jeton.
 *
 * `androidx.security:security-crypto` aurait fait le même travail, mais la
 * bibliothèque est dépréciée : l'employer contreviendrait à AGENTS.md §2. Le
 * code ci-dessous n'utilise que des API de la plateforme, toujours en vigueur.
 *
 * ⚠️ **Non couvert par les tests** : Robolectric ne simule pas
 * `AndroidKeyStore`. C'est précisément ce que [SecretCipher] permet de
 * contourner — tout ce qui l'entoure est éprouvé avec une implémentation de
 * test.
 */
@Singleton
internal class KeystoreSecretCipher @Inject constructor() : SecretCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, orCreateKey())

        // Le vecteur d'initialisation est engendré par le chiffreur et doit
        // accompagner le message : sans lui, le déchiffrement est impossible.
        // Il n'est pas secret.
        val encrypted = cipher.doFinal(plainText.toByteArray())
        return encode(cipher.iv) + SEPARATOR + encode(encrypted)
    }

    override fun decrypt(cipherText: String): String? = runCatching {
        val (iv, payload) = cipherText.split(SEPARATOR).let { it[0] to it[1] }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, orCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, decode(iv)))
        String(cipher.doFinal(decode(payload)))
    }.getOrElse { failure ->
        // Cas réel : l'utilisateur a changé son verrouillage d'écran, ou
        // restauré une sauvegarde sur un autre appareil. La clé est perdue, le
        // secret illisible — la session doit être traitée comme absente.
        Timber.w("Secret illisible, session considérée absente : %s", failure.javaClass.simpleName)
        null
    }

    private fun orCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        return existing ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Volontairement **sans** `setUserAuthenticationRequired` : le
                // flux Discover doit s'ouvrir sans redemander le déverrouillage
                // à chaque lancement. Le secret protégé est un jeton de lecture
                // de flux RSS, pas un moyen de paiement.
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "freshrss-discover.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val SEPARATOR = ":"
    }
}
