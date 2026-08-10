package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddressResult
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Les réponses décrites ici sont **littérales** : elles reproduisent la forme
 * relevée sur une instance réelle (docs/freshrss-api.md §2.3 et §4.1). En
 * particulier, `edit-tag` répond `OK` en texte brut, jamais du JSON.
 */
class FreshRssApiEditTagTest {
    private val address = (ServerAddress.parse("exemple.org") as ServerAddressResult.Valid).address
    private val token = AuthToken("alice/c0ffee")
    private val modificationToken = ModificationToken("Z".repeat(TOKEN_LENGTH))

    private var lastRequest: HttpRequestData? = null

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
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

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8"),
        )

    /** Le corps envoyé est un formulaire : c'est lui, et non l'URL, qui porte les champs. */
    private fun formParameters(): Parameters = (lastRequest?.body as FormDataContent).formData

    // ----- Jeton de modification ---------------------------------------------

    @Test
    fun theModificationTokenIsReadFromThePlainTextBody() = runTest {
        val body = "8Zqp4wA9yTfM".padEnd(TOKEN_LENGTH, 'Z')

        val outcome = api { text("$body\n") }.modificationToken(address, token)

        assertEquals(body, assertIs<ApiOutcome.Success<ModificationToken>>(outcome).value.value)
    }

    @Test
    fun theModificationTokenRequestCarriesTheSessionTokenAndTargetsTheTokenEndpoint() = runTest {
        api { text("Z".repeat(TOKEN_LENGTH)) }.modificationToken(address, token)

        assertEquals("GoogleLogin auth=alice/c0ffee", lastRequest?.headers?.get(HttpHeaders.Authorization))
        assertTrue(lastRequest?.url?.encodedPath?.endsWith("/reader/api/0/token") == true)
    }

    @Test
    fun aTokenOfAnUnexpectedLengthIsStillAccepted() = runTest {
        // Les 57 caractères sont un **constat**, pas un contrat. Les exiger
        // ferait échouer l'application sur un jeton parfaitement valide le jour
        // où FreshRSS en changerait la forme ; un jeton refusé se signale par
        // un 401 à l'usage (docs/freshrss-api.md §2.3).
        val outcome = api { text("court") }.modificationToken(address, token)

        assertEquals("court", assertIs<ApiOutcome.Success<ModificationToken>>(outcome).value.value)
    }

    @Test
    fun anEmptyTokenBodyIsMalformed() = runTest {
        // Un jeton vide ne produirait qu'un edit-tag silencieusement inutile.
        val outcome = api { text("\n") }.modificationToken(address, token)

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun anExpiredSessionRefusesTheModificationToken() = runTest {
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }.modificationToken(address, token)

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.Unauthorized.value, error.status)
        assertEquals("Unauthorized!", error.body)
    }

    @Test
    fun aNetworkFailureOnTheTokenIsReportedRatherThanThrown() = runTest {
        val outcome = api { throw IOException("hôte inconnu") }.modificationToken(address, token)

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    // ----- Marquage comme lu -------------------------------------------------

    @Test
    fun aBatchIsSentAsASingleRequestWithOneItemFieldPerArticle() = runTest {
        // Le traitement par lot est la raison d'être de ce point d'entrée : une
        // requête par article visible saturerait le réseau (SPECS.md §4.5).
        api { text("OK") }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L), ArticleId(2L), ArticleId(3L)))

        val form = formParameters()
        assertTrue(lastRequest?.url?.encodedPath?.endsWith("/reader/api/0/edit-tag") == true)
        assertEquals(listOf("1", "2", "3"), form.getAll("i"))
        assertEquals(modificationToken.value, form["T"])
        assertEquals("user/-/state/com.google/read", form["a"])
        assertEquals("GoogleLogin auth=alice/c0ffee", lastRequest?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun anIdentifierBeyondTheSignedRangeIsSentUnsigned() = runTest {
        // Les identifiants sont des entiers 64 bits **non signés** ; au-delà de
        // Long.MAX_VALUE ils se lisent négatifs en Kotlin. `toString()`
        // enverrait « -1 » : le serveur marquerait un article inexistant et
        // répondrait OK sans rien faire — la perte serait muette.
        api { text("OK") }.markAsRead(address, token, modificationToken, listOf(ArticleId(-1L), ArticleId(-2L)))

        assertEquals(listOf("18446744073709551615", "18446744073709551614"), formParameters().getAll("i"))
    }

    @Test
    fun anOkBodySignalsSuccess() = runTest {
        val outcome = api { text("OK\n") }.markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        assertIs<ApiOutcome.Success<Unit>>(outcome)
    }

    @Test
    fun anExpiredModificationTokenSurfacesItsStatusAndPlainTextBody() = runTest {
        // C'est ce 401 qui dira plus haut de redemander un jeton, puis de
        // traiter comme une perte de session si l'échec persiste.
        val outcome = api { text("Unauthorized!", HttpStatusCode.Unauthorized) }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        val error = assertIs<ApiOutcome.HttpError>(outcome)
        assertEquals(HttpStatusCode.Unauthorized.value, error.status)
        assertEquals("Unauthorized!", error.body)
    }

    @Test
    fun aBodyOtherThanOkIsMalformed() = runTest {
        // Portail captif ou page de maintenance qui répond 200 à tout : le
        // marquage n'a pas été pris en compte, et la file doit le conserver.
        val outcome = api { json("""{"status":"queued"}""") }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        assertIs<ApiOutcome.MalformedResponse>(outcome)
    }

    @Test
    fun aNetworkFailureOnTheMarkIsReportedRatherThanThrown() = runTest {
        val outcome = api { throw IOException("hôte inconnu") }
            .markAsRead(address, token, modificationToken, listOf(ArticleId(1L)))

        assertIs<ApiOutcome.TransportError>(outcome)
    }

    private companion object {
        /** Longueur constatée du jeton (docs/freshrss-api.md §2.3), utile aux seuls jeux d'essai. */
        const val TOKEN_LENGTH = 57
    }
}
