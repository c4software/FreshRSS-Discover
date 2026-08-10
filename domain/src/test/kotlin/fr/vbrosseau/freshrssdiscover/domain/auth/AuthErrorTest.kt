package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthErrorTest {
    /**
     * Verifies that each cause from SPECS.md §3.3 has its own case and that
     * none was merged with another during refactorings.
     *
     * Collapsing two causes into one compiles fine, but the specification
     * requires a distinct message per cause; otherwise the user cannot tell
     * whether to fix their password or enable the API on their server.
     */
    @Test
    fun everyDiagnosableCauseHasItsOwnCase() {
        val causes: List<AuthError> =
            listOf(
                AuthError.NoNetwork,
                AuthError.ServerUnreachable,
                AuthError.NotAFreshRssServer,
                AuthError.ApiDisabled,
                AuthError.InvalidCredentials,
                AuthError.AuthorizationHeaderNotForwarded,
                AuthError.Unexpected("peu importe"),
            )

        assertEquals(causes.size, causes.distinct().size)
    }

    @Test
    fun theTechnicalMessageIsNotPartOfTheIdentityOfOtherCases() {
        // Two `Unexpected` with different messages are two different errors;
        // the diagnosable cases are singletons comparable by equality, which
        // the presentation layer's `when` depends on.
        assertEquals(AuthError.ApiDisabled, AuthError.ApiDisabled)
        assertTrue(AuthError.Unexpected("a") != AuthError.Unexpected("b"))
    }
}
