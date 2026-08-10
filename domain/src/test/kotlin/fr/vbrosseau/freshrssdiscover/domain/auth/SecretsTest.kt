package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `toString` is called by logging libraries, exception messages, and the
 * debugger. A plain `data class` would have been enough to leak a password
 * into terminal logs without any line of code asking for it.
 */
class SecretsTest {
    @Test
    fun credentialsNeverRevealTheApiPassword() {
        val credentials = Credentials(username = "alice", apiPassword = "s3cr3t-tr3s-pr1v3")

        assertFalse("s3cr3t-tr3s-pr1v3" in credentials.toString())
    }

    @Test
    fun credentialsKeepTheUsernameVisible() {
        // The username is not a secret, and seeing it in logs helps diagnose
        // a sign-in failure.
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
        // Equality is used to detect that a stored token is still the right
        // one: losing it while hand-redefining `toString` would be a silent
        // regression.
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
