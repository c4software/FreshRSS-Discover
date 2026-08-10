package fr.vbrosseau.freshrssdiscover.data.security

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Tests the encryption of secrets at rest. */
@RunWith(RobolectricTestRunner::class)
class KeystoreSecretCipherTest {
    /**
     * An ordinary in-memory AES key.
     *
     * From the cipher's point of view it plays exactly the role of the hardware
     * keystore key; the `SecretKeySource` indirection is what makes this
     * substitution, and therefore this test, possible.
     */
    private val key: SecretKey = KeyGenerator.getInstance("AES")
        .apply { init(AES_KEY_BITS) }
        .generateKey()

    private val cipher = KeystoreSecretCipher { key }

    @Test
    fun whatIsEncryptedComesBackIdentical() {
        val restored = cipher.decrypt(cipher.encrypt("alice/c0ffee"))

        assertEquals("alice/c0ffee", restored)
    }

    @Test
    fun theCipherTextResemblesNothingOfTheSecret() {
        val encrypted = cipher.encrypt("alice/c0ffee")

        assertNotEquals("alice/c0ffee", encrypted)
        assertEquals(false, encrypted.contains("c0ffee"))
    }

    @Test
    fun theSameSecretEncryptedTwiceGivesTwoDifferentTexts() {
        // The initialization vector changes on every encryption: two identical
        // tokens must not produce identical writes, otherwise merely reading
        // the file would betray their equality.
        assertNotEquals(cipher.encrypt("un secret"), cipher.encrypt("un secret"))
    }

    @Test
    fun theInitialisationVectorTravelsWithTheMessage() {
        val encrypted = cipher.encrypt("un secret")

        assertEquals(2, encrypted.split(":").size)
    }

    @Test
    fun aTextThatIsNotOneIsReadAsAnAbsentSecret() {
        // Real-world case: screen lock changed, backup restored elsewhere. The
        // key is lost and the secret unreadable; the session must then be
        // treated as absent, never crash the application.
        assertNull(cipher.decrypt("n'importe quoi"))
    }

    @Test
    fun aTruncatedTextIsReadAsAnAbsentSecret() {
        val encrypted = cipher.encrypt("un secret")

        assertNull(cipher.decrypt(encrypted.substringBefore(":")))
    }

    @Test
    fun aTamperedPayloadIsRefused() {
        // GCM authenticates the message: a modified byte must make decryption
        // fail rather than return an approximate text.
        val (iv, payload) = cipher.encrypt("un secret").split(":")
        val tampered = payload.replaceFirst(payload.first(), if (payload.first() == 'A') 'B' else 'A')

        assertNull(cipher.decrypt("$iv:$tampered"))
    }

    @Test
    fun anEmptySecretSurvivesTheRoundTrip() {
        assertEquals("", cipher.decrypt(cipher.encrypt("")))
    }

    @Test
    fun aSecondInstanceReadsWhatTheFirstWrote() {
        // The key lives in the store, not in the object: a new cipher, which is
        // what every application launch creates, must read back secrets already
        // written.
        val encrypted = cipher.encrypt("alice/c0ffee")

        assertEquals("alice/c0ffee", KeystoreSecretCipher { key }.decrypt(encrypted))
    }

    private companion object {
        const val AES_KEY_BITS = 256
    }
}
