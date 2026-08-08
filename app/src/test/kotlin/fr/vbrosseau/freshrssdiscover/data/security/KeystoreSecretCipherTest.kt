package fr.vbrosseau.freshrssdiscover.data.security

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Le chiffrement des secrets au repos, éprouvé pour de bon.
 *
 * Il ne l'était pas : `AndroidKeyStore` n'était pas simulé, et la classe était
 * restée le seul composant de production sans test — celui-là même qui protège
 * le jeton FreshRSS. `SecretCipher` permettait de tester *autour* d'elle ; ce
 * fichier teste **elle**.
 */
@RunWith(RobolectricTestRunner::class)
class KeystoreSecretCipherTest {
    /**
     * Une clé AES ordinaire, en mémoire.
     *
     * Elle tient exactement le rôle de celle du magasin matériel du point de
     * vue du chiffreur : c'est le partage introduit par `SecretKeySource` qui
     * rend cette substitution possible, et donc ce fichier possible.
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
        // Le vecteur d'initialisation change à chaque chiffrement : deux jetons
        // identiques ne doivent pas produire deux écritures identiques, faute
        // de quoi la seule lecture du fichier trahirait leur égalité.
        assertNotEquals(cipher.encrypt("un secret"), cipher.encrypt("un secret"))
    }

    @Test
    fun theInitialisationVectorTravelsWithTheMessage() {
        val encrypted = cipher.encrypt("un secret")

        assertEquals(2, encrypted.split(":").size)
    }

    @Test
    fun aTextThatIsNotOneIsReadAsAnAbsentSecret() {
        // Cas réel : verrouillage d'écran changé, sauvegarde restaurée ailleurs.
        // La clé est perdue et le secret illisible — la session doit alors être
        // traitée comme absente, jamais faire tomber l'application.
        assertNull(cipher.decrypt("n'importe quoi"))
    }

    @Test
    fun aTruncatedTextIsReadAsAnAbsentSecret() {
        val encrypted = cipher.encrypt("un secret")

        assertNull(cipher.decrypt(encrypted.substringBefore(":")))
    }

    @Test
    fun aTamperedPayloadIsRefused() {
        // GCM authentifie le message : un octet modifié doit faire échouer le
        // déchiffrement plutôt que rendre un texte approchant.
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
        // La clé vit dans le magasin, pas dans l'objet : un nouveau chiffreur
        // — ce qu'est chaque lancement de l'application — doit relire les
        // secrets déjà écrits.
        val encrypted = cipher.encrypt("alice/c0ffee")

        assertEquals("alice/c0ffee", KeystoreSecretCipher { key }.decrypt(encrypted))
    }

    private companion object {
        const val AES_KEY_BITS = 256
    }
}
