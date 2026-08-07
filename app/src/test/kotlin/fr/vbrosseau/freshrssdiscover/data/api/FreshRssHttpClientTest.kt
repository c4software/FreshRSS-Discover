package fr.vbrosseau.freshrssdiscover.data.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve la configuration du client, pas encore les points d'entrée.
 *
 * Chacun de ces réglages a une conséquence directe et non évidente sur le
 * traitement des réponses de FreshRSS : les vérifier ici évite d'avoir à
 * redécouvrir leur effet à travers un échec d'appel métier.
 */
class FreshRssHttpClientTest {
    /** `Logger` de Ktor n'est pas une `fun interface` : il faut un objet. */
    private fun recordingLogger(into: MutableList<String>) = object : Logger {
        override fun log(message: String) {
            into += message
        }
    }

    private fun clientRespondingWith(
        status: HttpStatusCode,
        body: String,
        contentType: String,
        verboseLogging: Boolean = false,
        logger: Logger = recordingLogger(mutableListOf()),
    ) = createFreshRssHttpClient(
        engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        },
        verboseLogging = verboseLogging,
        logger = logger,
    )

    @Test
    fun anErrorStatusIsReturnedRatherThanThrown() = runTest {
        // Les codes de FreshRSS *sont* l'information utile : 503 signifie
        // « API désactivée », 401 « jeton invalide ». Les laisser lever
        // obligerait à les reconstituer depuis une exception.
        val client = clientRespondingWith(
            HttpStatusCode.ServiceUnavailable,
            "Service Unavailable!",
            "text/plain; charset=UTF-8",
        )

        val response = client.get("https://exemple.org/api/greader.php")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("Service Unavailable!", response.bodyAsText())
    }

    @Test
    fun aPlainTextErrorBodyIsReadableAsText() = runTest {
        // FreshRSS répond ses erreurs en text/plain, jamais en JSON. Une
        // négociation de contenu trop large tenterait de les désérialiser,
        // échouerait, et masquerait le vrai code.
        val client = clientRespondingWith(
            HttpStatusCode.Unauthorized,
            "Unauthorized!",
            "text/plain; charset=UTF-8",
        )

        val response = client.get("https://exemple.org/api/greader.php/reader/api/0/user-info")

        assertEquals("Unauthorized!", response.bodyAsText())
    }

    @Test
    fun theRecognitionProbeReadsItsTwoLetterBody() = runTest {
        // La sonde de reconnaissance répond `OK` avec un Content-Type
        // text/html — se fier au type MIME ne marcherait pas.
        val client = clientRespondingWith(HttpStatusCode.OK, "OK", "text/html; charset=UTF-8")

        assertEquals("OK", client.get("https://exemple.org/api/greader.php").bodyAsText())
    }

    @Test
    fun theAuthorizationHeaderNeverReachesTheLogs() = runTest {
        val lines = mutableListOf<String>()
        val client = clientRespondingWith(
            status = HttpStatusCode.OK,
            body = "OK",
            contentType = "text/plain",
            verboseLogging = true,
            logger = recordingLogger(lines),
        )

        client.get("https://exemple.org/api/greader.php") {
            header(HttpHeaders.Authorization, "GoogleLogin auth=alice/c0ffee")
        }

        val logged = lines.joinToString("\n")
        assertTrue(logged.isNotEmpty(), "la journalisation détaillée devrait produire des lignes")
        assertFalse("alice/c0ffee" in logged, "le jeton ne doit jamais atteindre les journaux")
    }

    @Test
    fun nothingIsLoggedWhenVerboseLoggingIsOff() = runTest {
        // C'est la configuration des constructions publiées : même expurgée,
        // la journalisation exposerait l'adresse du serveur personnel.
        val lines = mutableListOf<String>()
        val client = clientRespondingWith(
            status = HttpStatusCode.OK,
            body = "OK",
            contentType = "text/plain",
            verboseLogging = false,
            logger = recordingLogger(lines),
        )

        client.get("https://exemple.org/api/greader.php")

        assertTrue(lines.isEmpty(), "aucune ligne ne devrait être journalisée")
    }

    @Test
    fun unknownJsonFieldsAreTolerated() {
        // FreshRSS ajoute des champs au fil de ses versions. Sans cette
        // tolérance, une mise à jour du serveur casserait toutes les lectures.
        val decoded = FreshRssJson.decodeFromString(
            ProbeResponse.serializer(),
            """{"id":"feed/1","champInconnuAjouteParUneFutureVersion":42}""",
        )

        assertEquals("feed/1", decoded.id)
    }

    @kotlinx.serialization.Serializable
    private data class ProbeResponse(val id: String)
}
