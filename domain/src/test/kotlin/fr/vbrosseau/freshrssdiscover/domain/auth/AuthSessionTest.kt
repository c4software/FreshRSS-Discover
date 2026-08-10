package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthSessionTest {
    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address

    private fun session(modificationToken: ModificationToken? = null) =
        AuthSession(
            server = server,
            username = "alice",
            token = AuthToken("alice/c0ffee"),
            modificationToken = modificationToken,
        )

    @Test
    fun aFreshSessionHasNoModificationToken() {
        // Obtained through a separate call that would be wasteful on every
        // sign-in: only mutating operations need it.
        assertNull(session().modificationToken)
    }

    @Test
    fun theModificationTokenIsAddedWithoutRebuildingTheSession() {
        val enriched = session().copy(modificationToken = ModificationToken("ZZZ"))

        assertNotNull(enriched.modificationToken)
        assertEquals("alice", enriched.username)
        assertEquals(server, enriched.server)
    }

    @Test
    fun theGeneratedToStringLeaksNoToken() {
        // A `data class` prints all its fields. That is only safe here
        // because both tokens mask their own value; checking it prevents a
        // future secret field from leaking in clear text.
        val text = session(ModificationToken("ZZZ-secret")).toString()

        assertFalse("alice/c0ffee" in text)
        assertFalse("ZZZ-secret" in text)
    }

    @Test
    fun sessionsCompareByValue() {
        assertEquals(session(), session())
        assertEquals(session().hashCode(), session().hashCode())
    }
}
