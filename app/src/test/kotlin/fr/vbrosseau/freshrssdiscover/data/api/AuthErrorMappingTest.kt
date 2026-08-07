package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthError
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SPECS.md §3.3 exige un message par cause. Cette traduction est le seul
 * endroit qui décide laquelle : s'y tromper affiche un diagnostic faux, et
 * l'utilisateur cherche alors du côté qu'on lui a désigné.
 */
class AuthErrorMappingTest {
    @Test
    fun anUnreachableServerIsDistinguishedFromAnAbsentNetwork() {
        // La pile HTTP rapporte les deux de façon identique, et les gestes de
        // correction n'ont rien à voir : attendre le réseau, ou corriger
        // l'adresse. Seule la connectivité constatée les sépare.
        val failure = ApiOutcome.TransportError(IOException("hôte inconnu"))

        assertEquals(AuthError.ServerUnreachable, failure.toAuthError(isOnline = true))
        assertEquals(AuthError.NoNetwork, failure.toAuthError(isOnline = false))
    }

    @Test
    fun aWellFormedButNonFreshRssResponseNamesTheRightCause() {
        // Portail captif, page de maintenance, proxy qui répond 200 à tout.
        val outcome = ApiOutcome.MalformedResponse("la racine n'a pas répondu « OK »")

        assertEquals(AuthError.NotAFreshRssServer, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a401MeansCredentialsWhicheverOfTheTwoIsWrong() {
        // Constaté : identifiant inconnu et mot de passe faux répondent tous
        // deux 401. Les distinguer permettrait d'énumérer les comptes.
        val outcome = ApiOutcome.HttpError(401, "Unauthorized!")

        assertEquals(AuthError.InvalidCredentials, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a400AlsoSendsTheUserBackToHisCredentials() {
        // 400 désigne un identifiant syntaxiquement invalide. La cause diffère
        // du 401, mais le geste attendu est le même.
        val outcome = ApiOutcome.HttpError(400, "Bad Request!")

        assertEquals(AuthError.InvalidCredentials, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a503MeansTheApiIsDisabledNotThatTheCredentialsAreWrong() {
        // C'est une case à cocher dans l'administration FreshRSS. Le confondre
        // avec un refus d'identifiants ferait vérifier son mot de passe
        // indéfiniment.
        val outcome = ApiOutcome.HttpError(503, "Service Unavailable!")

        assertEquals(AuthError.ApiDisabled, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun a404DesignatesTheHostNotThePath() {
        // Constaté : un chemin inconnu sous l'API répond 401, jamais 404. Un
        // 404 ne peut donc venir que d'un hôte qui n'est pas FreshRSS.
        val outcome = ApiOutcome.HttpError(404, "Not Found")

        assertEquals(AuthError.NotAFreshRssServer, outcome.toAuthError(isOnline = true))
    }

    @Test
    fun anUnhandledStatusKeepsItsCodeForTheLogs() {
        val outcome = ApiOutcome.HttpError(500, "Internal Server Error")

        val error = assertIs<AuthError.Unexpected>(outcome.toAuthError(isOnline = true))
        assertTrue("500" in error.technicalMessage)
    }

    @Test
    fun a501IsReportedAsUnexpectedBecauseItIsAProgrammingFault() {
        // 501 signifie « output autre que json là où il est exigé » : aucune
        // action de l'utilisateur n'y changerait quoi que ce soit.
        val outcome = ApiOutcome.HttpError(501, "Not Implemented!")

        assertIs<AuthError.Unexpected>(outcome.toAuthError(isOnline = true))
    }

    @Test
    fun aLongErrorBodyIsTruncatedBeforeReachingTheLogs() {
        // Un portail captif renvoie une page HTML entière : la déverser dans
        // les journaux les rendrait illisibles.
        val outcome = ApiOutcome.HttpError(500, "x".repeat(10_000))

        val error = assertIs<AuthError.Unexpected>(outcome.toAuthError(isOnline = true))
        assertTrue(error.technicalMessage.length < 300, "message de ${error.technicalMessage.length} caractères")
    }

    @Test
    fun theConnectivityOnlyMattersForTransportFailures() {
        // Un serveur qui a répondu prouve que le réseau fonctionne : laisser la
        // connectivité influer sur un code HTTP produirait des diagnostics
        // incohérents.
        val outcome = ApiOutcome.HttpError(503, "Service Unavailable!")

        assertEquals(outcome.toAuthError(isOnline = true), outcome.toAuthError(isOnline = false))
    }

    @Test
    fun mappingASuccessIsReportedAsAProgrammingFault() {
        // Ne devrait jamais arriver ; le signaler vaut mieux que de renvoyer
        // une erreur plausible qui masquerait l'anomalie.
        val error = ApiOutcome.Success(Unit).toAuthError(isOnline = true)

        assertIs<AuthError.Unexpected>(error)
    }
}
