package fr.vbrosseau.freshrssdiscover.data.security

import android.util.Base64
import timber.log.Timber
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chiffrement AES/GCM des secrets écrits sur disque.
 *
 * La clé vient de [SecretKeySource] et n'est pas fabriquée ici : c'est ce
 * partage qui rend cette classe éprouvable. Le magasin matériel, lui, reste
 * hors de portée des tests — voir [SecretKeySource].
 *
 * **GCM et non CBC** : le mode authentifie le message autant qu'il le chiffre.
 * Un octet modifié dans le fichier de préférences fait échouer le
 * déchiffrement au lieu de rendre un texte approchant, que l'application
 * prendrait pour un jeton.
 */
@Singleton
internal class KeystoreSecretCipher @Inject constructor(
    private val keys: SecretKeySource,
) : SecretCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())

        // Le vecteur d'initialisation est engendré par le chiffreur et doit
        // accompagner le message : sans lui, le déchiffrement est impossible.
        // Il n'est pas secret.
        val encrypted = cipher.doFinal(plainText.toByteArray())
        return encode(cipher.iv) + SEPARATOR + encode(encrypted)
    }

    override fun decrypt(cipherText: String): String? = runCatching {
        val (iv, payload) = cipherText.split(SEPARATOR).let { it[0] to it[1] }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_LENGTH_BITS, decode(iv)))
        String(cipher.doFinal(decode(payload)))
    }.getOrElse { failure ->
        // Cas réel : l'utilisateur a changé son verrouillage d'écran, ou
        // restauré une sauvegarde sur un autre appareil. La clé est perdue, le
        // secret illisible — la session doit être traitée comme absente.
        Timber.w("Secret illisible, session considérée absente : %s", failure.javaClass.simpleName)
        null
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val SEPARATOR = ":"
    }
}
