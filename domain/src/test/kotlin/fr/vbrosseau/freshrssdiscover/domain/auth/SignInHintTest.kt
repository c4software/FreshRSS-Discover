package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SignInHintTest {
    private val server = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address

    @Test
    fun aHintCarriesNoSecret() {
        // C'est toute sa raison d'être : il survit à un jeton refusé, là où les
        // jetons sont effacés. Y glisser un secret annulerait cette propriété.
        val rendered = SignInHint(server, "alice").toString()

        // `toString` d'une data class expose tous les champs : le vérifier
        // signale immédiatement l'ajout d'un champ sensible.
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
        // Préremplir avec la forme canonique évite qu'une nouvelle tentative
        // reparte d'une saisie approximative.
        assertEquals("https://exemple.org", SignInHint(server, "alice").server.baseUrl)
    }
}
