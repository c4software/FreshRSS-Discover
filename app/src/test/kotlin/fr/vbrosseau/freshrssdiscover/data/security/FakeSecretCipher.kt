package fr.vbrosseau.freshrssdiscover.data.security

/**
 * Test cipher: reversible, observable, and deliberately not secure.
 *
 * Robolectric does not simulate `AndroidKeyStore`; without this fake, nothing
 * around the encryption would be testable. The prefix makes it visible in
 * assertions that a value went through the cipher; a plain-text write would
 * stand out.
 */
internal class FakeSecretCipher(
    /** Simulates a lost keystore key: every decryption fails. */
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
