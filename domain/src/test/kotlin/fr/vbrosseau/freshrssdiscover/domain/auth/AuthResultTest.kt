package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthResultTest {
    @Test
    fun aSuccessExposesItsValueAndNoError() {
        val result: AuthResult<String> = AuthResult.Success("jeton")

        assertEquals("jeton", result.valueOrNull())
        assertNull(result.errorOrNull())
    }

    @Test
    fun aFailureExposesItsErrorAndNoValue() {
        val result: AuthResult<String> = AuthResult.Failure(AuthError.ApiDisabled)

        assertNull(result.valueOrNull())
        assertEquals(AuthError.ApiDisabled, result.errorOrNull())
    }

    @Test
    fun anUnexpectedErrorCarriesItsTechnicalMessage() {
        // Ce message est destiné aux journaux, pas à l'affichage : il doit
        // survivre au transport, sans quoi un diagnostic devient impossible.
        val error = AuthError.Unexpected("SSLHandshakeException: certificat expiré")

        assertEquals("SSLHandshakeException: certificat expiré", error.technicalMessage)
    }
}
