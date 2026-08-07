package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Les réponses décrites ici sont **littérales** : elles reproduisent ce que
 * `https://demo.freshrss.org/` a réellement renvoyé (docs/freshrss-api.md §1
 * et §2), pas ce qu'une lecture de la source laissait supposer.
 */
class FreshRssApiTest {
    private val address = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val credentials = Credentials("alice", "mot-de-passe-api")

    private var lastRequest: HttpRequestData? = null

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        FreshRssApi(
            createFreshRssHttpClient(
                MockEngine { request ->
                    lastRequest = request
                    handler(request)
                },
            ),
        )

    private fun MockRequestHandleScope.text(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8"),
        )

    // ----- Sonde de reconnaissance -------------------------------------------

    @Test
    fun theProbeAcceptsTheTwoLetterBody() = runTest {
        // Constaté : le Content-Type est text/html, pas text/plain. Se fier au
        // type MIME rejetterait une instance parfaitement valide.
        val outcome = api {
            respond("OK", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
        }.probe(address)

        assertIs<ApiOutcome.Success<Unit>>(outcome)
    }

    @Test
    fun theProbeTargetsTheApiEndpointWithoutAnyQueryString() = runTest {
        // Constaté : la moindre chaîne de requête fait répondre 400 au lieu de
        // OK. Une sonde qui en ajouterait une échouerait sur tout serveur.
        api { text("OK") }.probe(address)

        assertEquals("https://exemple.org/api/greader.php", lastRequest?.url.toString())
        assertEquals("", lastRequest?.url?.encodedQuery)
    }

    @Test
    fun aServerAnsweringSomethingElseIsReportedAsMalformedNotAsAnHttpError() = runTest {
        // Cas réel : portail captif, page de maintenance, proxy qui répond 200
        // à tout. Le traiter comme une erreur HTTP afficherait un faux
        // diagnostic.
        val outcome = api {
            respond("<html>Bienvenue</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }.probe(address)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aHostThatIsNotFreshRssSurfacesItsHttpStatus() = runTest {
        val outcome = api { respondError(HttpStatusCode.NotFound) }.probe(address)

        assertEquals(HttpStatusCode.NotFound.value, assertIs<ApiOutcome.HttpError>(outcome).status)
    }

    // ----- Sonde de transmission de l'en-tête --------------------------------

    @Test
    fun theCompatibilityProbeSendsAnAuthorizationHeaderOfItsOwn() = runTest {
        // Constaté : sans en-tête dans la requête, la sonde répond FAIL même
        // sur un serveur correctement configuré. Elle constate la présence de
        // l'en-tête qu'elle reçoit, pas sa validité.
        api { text("PASS") }.checkAuthorizationForwarding(address)

        assertTrue(lastRequest?.headers?.contains(HttpHeaders.Authorization) == true)
    }

    @Test
    fun theCompatibilityProbeReadsTheBodyNotTheStatus() = runTest {
        // Constaté : le statut est 200 dans les deux cas. Tester le code HTTP
        // ne vérifierait rien du tout.
        val failing = api { text("FAIL get HTTP Authorization header! Wrong Web server configuration.") }
            .checkAuthorizationForwarding(address)

        assertFalse(assertIs<ApiOutcome.Success<Boolean>>(failing).value)

        val passing = api { text("PASS") }.checkAuthorizationForwarding(address)
        assertTrue(assertIs<ApiOutcome.Success<Boolean>>(passing).value)
    }

    // ----- ClientLogin -------------------------------------------------------

    @Test
    fun clientLoginExtractsTheAuthLineAndIgnoresTheOthers() = runTest {
        val outcome = api {
            text("SID=alice/c0ffee\nLSID=null\nAuth=alice/c0ffee\n")
        }.clientLogin(address, credentials)

        // `SID` porte la même valeur et `LSID` vaut `null` : seule `Auth`
        // compte, et la retenir par sa clé plutôt que par son rang protège
        // d'un changement d'ordre des lignes.
        assertEquals("alice/c0ffee", assertIs<ApiOutcome.Success<AuthToken>>(outcome).value.value)
    }

    @Test
    fun clientLoginPostsTheCredentialsRatherThanPuttingThemInTheUrl() = runTest {
        // FreshRSS accepte aussi GET, mais journalise alors un avertissement :
        // le mot de passe apparaîtrait dans les journaux du serveur.
        api { text("Auth=alice/c0ffee") }.clientLogin(address, credentials)

        assertEquals(HttpMethod.Post, lastRequest?.method)
        assertEquals(
            "https://exemple.org/api/greader.php/accounts/ClientLogin",
            lastRequest?.url.toString(),
        )
        assertFalse("mot-de-passe-api" in lastRequest?.url.toString())
    }

    @Test
    fun clientLoginToleratesAResponseWithoutTrailingNewline() = runTest {
        val outcome = api { text("Auth=alice/c0ffee") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.Success<*>>(outcome)
    }

    @Test
    fun aRefusedLoginSurfacesItsStatusAndPlainTextBody() = runTest {
        // Constaté : identifiant inconnu et mot de passe faux répondent tous
        // deux 401 « Unauthorized! ». La couche API ne les distingue pas — elle
        // n'a pas à le faire, ils sont indistinguables.
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }
            .clientLogin(address, credentials)

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.Unauthorized.value, error.status)
        assertEquals("Unauthorized!", error.body)
    }

    @Test
    fun aSyntacticallyInvalidUsernameSurfacesA400() = runTest {
        val outcome = api { text("Bad Request!", HttpStatusCode.BadRequest) }
            .clientLogin(address, Credentials("nom invalide!", "x"))

        assertEquals(HttpStatusCode.BadRequest.value, assertIs<ApiOutcome.HttpError>(outcome).status)
    }

    @Test
    fun aSuccessfulStatusWithoutAnAuthLineIsMalformed() = runTest {
        // Un portail captif répondant 200 à tout tomberait ici. Le confondre
        // avec un succès produirait un jeton vide, et un 401 inexplicable au
        // premier appel suivant.
        val outcome = api { text("SID=alice/c0ffee\nLSID=null\n") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun anEmptyAuthValueIsMalformed() = runTest {
        val outcome = api { text("Auth=\n") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aTruncatedResponseIsMalformedRatherThanAnError() = runTest {
        val outcome = api { text("Au") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aJsonResponseWhereTextWasExpectedIsMalformed() = runTest {
        // Un serveur mal configuré, ou une passerelle d'API interposée.
        val outcome = api {
            respond(
                ByteReadChannel("""{"error":"nope"}"""),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.clientLogin(address, credentials)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    // ----- Transport ---------------------------------------------------------

    @Test
    fun aNetworkFailureIsReportedRatherThanThrown() = runTest {
        // Sans cela, chaque appelant devrait connaître les exceptions de Ktor.
        val outcome = api { throw IOException("hôte inconnu") }.probe(address)

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    @Test
    fun clientLoginAlsoReportsANetworkFailure() = runTest {
        val outcome = api { throw IOException("délai dépassé") }.clientLogin(address, credentials)

        assertIs<ApiOutcome.TransportError>(outcome)
    }
}
