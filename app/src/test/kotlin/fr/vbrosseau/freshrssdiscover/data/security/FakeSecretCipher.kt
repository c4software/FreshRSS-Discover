package fr.vbrosseau.freshrssdiscover.data.security

/**
 * Chiffrement de test : réversible, observable, et volontairement **pas**
 * sécurisé.
 *
 * Robolectric ne simule pas `AndroidKeyStore` ; sans ce double, rien de ce qui
 * entoure le chiffrement ne serait éprouvable. Le préfixe rend visible dans les
 * assertions qu'une valeur est bien passée par le chiffreur — une écriture en
 * clair sauterait aux yeux.
 */
internal class FakeSecretCipher(
    /** Simule une clé de *keystore* perdue : tout déchiffrement échoue. */
    var keyIsLost: Boolean = false,
) : SecretCipher {
    override fun encrypt(plainText: String): String = PREFIX + plainText.reversed()

    override fun decrypt(cipherText: String): String? = when {
        keyIsLost -> null
        !cipherText.startsWith(PREFIX) -> null
        else -> cipherText.removePrefix(PREFIX).reversed()
    }

    companion object {
        const val PREFIX = "chiffre:"
    }
}
