package fr.vbrosseau.freshrssdiscover.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthErrorTest {
    /**
     * Constate que chaque cause de SPECS.md §3.3 a bien son cas, et qu'aucune
     * n'a été fusionnée avec une autre au fil des refontes.
     *
     * Le test paraît trivial ; ce qu'il protège ne l'est pas. Ramener deux
     * causes à une seule compile parfaitement, et la spécification exige un
     * message distinct par cause — l'utilisateur ne saurait alors plus s'il
     * doit corriger son mot de passe ou activer l'API sur son serveur.
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
        // Deux `Unexpected` de messages différents sont deux erreurs
        // différentes ; les cas diagnosticables, eux, sont des singletons
        // comparables par égalité — c'est ce dont dépend le `when` de la
        // couche présentation.
        assertEquals(AuthError.ApiDisabled, AuthError.ApiDisabled)
        assertTrue(AuthError.Unexpected("a") != AuthError.Unexpected("b"))
    }
}
