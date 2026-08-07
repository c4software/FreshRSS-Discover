package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Le point vérifié ici n'est pas cosmétique : `toString` est appelé par les
 * bibliothèques de journalisation, par les messages d'exception et par le
 * débogueur. Une `data class` aurait suffi à faire apparaître un mot de passe
 * dans le journal du terminal, sans qu'aucune ligne de code ne l'ait demandé.
 */
class SecretsTest {
    @Test
    fun credentialsNeverRevealTheApiPassword() {
        val credentials = Credentials(username = "alice", apiPassword = "s3cr3t-tr3s-pr1v3")

        assertFalse("s3cr3t-tr3s-pr1v3" in credentials.toString())
    }

    @Test
    fun credentialsKeepTheUsernameVisible() {
        // L'identifiant n'est pas un secret, et le voir en journal aide à
        // diagnostiquer un échec de connexion.
        assertTrue("alice" in Credentials("alice", "peu importe").toString())
    }

    @Test
    fun authTokenNeverRevealsItsValue() {
        assertFalse("alice/abcdef" in AuthToken("alice/abcdef").toString())
    }

    @Test
    fun modificationTokenNeverRevealsItsValue() {
        assertFalse("ZZZZ-jeton" in ModificationToken("ZZZZ-jeton").toString())
    }

    @Test
    fun secretsCompareByValue() {
        // L'égalité sert à détecter qu'un jeton stocké est encore le bon : la
        // perdre en redéfinissant `toString` à la main serait une régression
        // silencieuse.
        assertEquals(AuthToken("a"), AuthToken("a"))
        assertEquals(AuthToken("a").hashCode(), AuthToken("a").hashCode())
        assertNotEquals(AuthToken("a"), AuthToken("b"))

        assertEquals(ModificationToken("a"), ModificationToken("a"))
        assertEquals(ModificationToken("a").hashCode(), ModificationToken("a").hashCode())
        assertNotEquals(ModificationToken("a"), ModificationToken("b"))

        assertEquals(Credentials("alice", "x"), Credentials("alice", "x"))
        assertEquals(Credentials("alice", "x").hashCode(), Credentials("alice", "x").hashCode())
        assertNotEquals(Credentials("alice", "x"), Credentials("alice", "y"))
        assertNotEquals(Credentials("alice", "x"), Credentials("bob", "x"))
    }

    @Test
    fun secretsAreNotEqualToOtherTypes() {
        assertNotEquals<Any?>(AuthToken("a"), "a")
        assertNotEquals<Any?>(ModificationToken("a"), "a")
        assertNotEquals<Any?>(Credentials("alice", "x"), "alice")
    }
}
