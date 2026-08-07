package fr.vbrosseau.freshrssdiscover.domain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeTest {
    private enum class Cause { Refused, Unreachable }

    @Test
    fun aSuccessExposesItsValueAndNoError() {
        val outcome: Outcome<String, Cause> = Outcome.Success("jeton")

        assertEquals("jeton", outcome.valueOrNull())
        assertNull(outcome.errorOrNull())
    }

    @Test
    fun aFailureExposesItsErrorAndNoValue() {
        val outcome: Outcome<String, Cause> = Outcome.Failure(Cause.Refused)

        assertNull(outcome.valueOrNull())
        assertEquals(Cause.Refused, outcome.errorOrNull())
    }

    @Test
    fun theErrorTypeIsCarriedByTheCallerNotImposed() {
        // C'est la raison d'être du paramètre de type : chaque domaine a ses
        // causes. Les fondre en une énumération unique obligerait à traiter des
        // cas impossibles — un article ne peut pas échouer parce que « l'API est
        // désactivée », l'authentification l'aurait déjà signalé.
        val authLike: Outcome<Int, Cause> = Outcome.Failure(Cause.Unreachable)
        val feedLike: Outcome<Int, String> = Outcome.Failure("fin de flux inattendue")

        assertEquals(Cause.Unreachable, authLike.errorOrNull())
        assertEquals("fin de flux inattendue", feedLike.errorOrNull())
    }

    @Test
    fun outcomesCompareByValue() {
        assertEquals(Outcome.Success("a"), Outcome.Success("a"))
        assertEquals(Outcome.Success("a").hashCode(), Outcome.Success("a").hashCode())
        assertEquals(Outcome.Failure(Cause.Refused), Outcome.Failure(Cause.Refused))
        assertEquals(Outcome.Failure(Cause.Refused).hashCode(), Outcome.Failure(Cause.Refused).hashCode())
    }

    @Test
    fun aSuccessAndAFailureAreNeverEqual() {
        assertEquals(false, Outcome.Success("a") == Outcome.Failure("a"))
    }
}
