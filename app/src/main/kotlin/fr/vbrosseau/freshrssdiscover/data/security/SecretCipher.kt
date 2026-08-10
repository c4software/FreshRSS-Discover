package fr.vbrosseau.freshrssdiscover.data.security

/**
 * Encrypts and decrypts secrets before they are written to disk.
 *
 * The abstraction exists for one precise reason: the real implementation
 * relies on `AndroidKeyStore`, which Robolectric cannot simulate. Without it,
 * session storage would be untestable — neither persistence nor the wipe on
 * logout could be verified, which is where the faults hide.
 */
internal interface SecretCipher {
    /** Encrypts [plainText] and returns a text-transportable form. */
    fun encrypt(plainText: String): String

    /**
     * Decrypts [cipherText], or returns `null` if impossible.
     *
     * The `null` is not theoretical: the keystore key is lost when the user
     * changes their screen lock or restores a backup on another device. The
     * secret then becomes unreadable, and the only correct behavior is to
     * treat the session as absent — not to crash.
     */
    fun decrypt(cipherText: String): String?
}
