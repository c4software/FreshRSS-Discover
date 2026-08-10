package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SignInHintTest {
    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address

    @Test
    fun aHintCarriesNoSecret() {
        // The hint's whole purpose: it survives a rejected token where tokens
        // get erased. Slipping a secret into it would void that property.
        val rendered = SignInHint(server, "alice").toString()

        // A data class's `toString` exposes all fields: checking it flags the
        // addition of a sensitive field immediately.
        assertEquals("SignInHint(server=ServerAddress(https://exemple.org), username=alice)", rendered)
        assertFalse("jeton" in rendered)
    }

    @Test
    fun hintsCompareByValue() {
        assertEquals(SignInHint(server, "alice"), SignInHint(server, "alice"))
        assertEquals(SignInHint(server, "alice").hashCode(), SignInHint(server, "alice").hashCode())
    }

    @Test
    fun theHintKeepsTheNormalizedAddress() {
        // Prefilling with the canonical form keeps a retry from starting over
        // with an approximate input.
        assertEquals("https://exemple.org", SignInHint(server, "alice").server.baseUrl)
    }
}
