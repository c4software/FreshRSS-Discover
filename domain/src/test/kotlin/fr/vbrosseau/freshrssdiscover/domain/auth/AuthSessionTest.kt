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
        // Il s'obtient par un appel distinct, qu'il serait inutile de payer à
        // chaque connexion : seules les opérations modifiantes en ont besoin.
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
        // Une `data class` affiche tous ses champs. C'est sans risque ici
        // uniquement parce que les deux jetons masquent eux-mêmes leur valeur ;
        // le vérifier évite qu'un futur champ secret passe en clair.
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
