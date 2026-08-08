package fr.vbrosseau.freshrssdiscover.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D'où vient la clé de chiffrement des secrets.
 *
 * **Cette interface existe pour une raison, et une seule** : `AndroidKeyStore`
 * n'est pas simulé par Robolectric, et il ne l'est toujours pas — réessayé le
 * 2026-08-08, le fournisseur lève `NoSuchAlgorithmException`. Tant que la
 * provenance de la clé était mêlée au chiffrement, c'est **toute** la classe
 * qui restait hors de portée des tests : le format, l'authentification GCM, la
 * conduite à tenir devant un texte illisible.
 *
 * Le partage se fait donc ici, au plus petit endroit possible. Ce qui reste non
 * testé est [AndroidKeyStoreKeySource] et rien d'autre — une vingtaine de
 * lignes qui ne font qu'appeler la plateforme.
 */
internal fun interface SecretKeySource {
    fun key(): SecretKey
}

/**
 * La clé vit dans le magasin matériel et n'en sort jamais.
 *
 * Même une lecture du fichier de préférences — appareil déverrouillé par un
 * tiers, sauvegarde extraite — ne livre pas le jeton.
 *
 * `androidx.security:security-crypto` aurait fait le même travail, mais la
 * bibliothèque est dépréciée : l'employer contreviendrait à AGENTS.md §2. Ce
 * qui suit n'utilise que des API de la plateforme, toujours en vigueur.
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
                // Volontairement **sans** `setUserAuthenticationRequired` : le
                // flux Discover doit s'ouvrir sans redemander le déverrouillage
                // à chaque lancement. Le secret protégé est un jeton de lecture
                // de flux RSS, pas un moyen de paiement.
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "freshrss-discover.session"
    }
}
