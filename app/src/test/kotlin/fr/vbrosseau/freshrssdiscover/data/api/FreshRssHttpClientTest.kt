package fr.vbrosseau.freshrssdiscover.data.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parameters
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the client configuration, not the endpoints.
 *
 * Each of these settings has a direct, non-obvious consequence on how
 * FreshRSS responses are handled: verifying them here avoids rediscovering
 * their effect through a failed business call.
 */
class FreshRssHttpClientTest {
    /** Ktor's `Logger` is not a `fun interface`: an object is required. */
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
        // FreshRSS status codes are the useful information: 503 means API
        // disabled, 401 means invalid token. Letting them throw would force
        // reconstructing them from an exception.
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
        // FreshRSS answers its errors as text/plain, never JSON. Overly
        // broad content negotiation would try to deserialize them, fail,
        // and mask the real status code.
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
        // The recognition probe answers `OK` with a text/html Content-Type;
        // trusting the MIME type would not work.
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

    /**
     * The two halves of the same rule, and neither is worth much alone.
     *
     * Bodies are logged because `edit-tag`'s is the only place where the `i`
     * fields actually sent are visible — and since the endpoint answers `OK`
     * with no per-article report (docs/freshrss-api.md §4.1), an identifier the
     * server ignores leaves no other trace. What keeps the API password out is
     * the filter on [CLIENT_LOGIN_PATH], not the level. Locking only one of the
     * two would let the other silently drift: raising the level without the
     * filter leaks the password, adding the filter without the level makes the
     * whole thing pointless.
     */
    @Test
    fun theClientLoginBodyNeverReachesTheLogs() = runTest {
        val lines = mutableListOf<String>()
        val client = clientRespondingWith(
            status = HttpStatusCode.OK,
            body = "SID=x\nAuth=alice/c0ffee\n",
            contentType = "text/plain",
            verboseLogging = true,
            logger = recordingLogger(lines),
        )

        client.submitForm(
            url = "https://exemple.org/api/greader.php$CLIENT_LOGIN_PATH",
            formParameters = parameters {
                append("Email", "alice")
                append("Passwd", "motDePasseApi")
            },
        ).bodyAsText()

        val logged = lines.joinToString("\n")
        assertFalse("motDePasseApi" in logged, "le mot de passe API ne doit jamais atteindre les journaux")
        assertFalse("Passwd" in logged, "le corps de ClientLogin ne doit pas être journalisé du tout")
    }

    @Test
    fun theEditTagBodyReachesTheLogsWithItsItems() = runTest {
        val lines = mutableListOf<String>()
        val client = clientRespondingWith(
            status = HttpStatusCode.OK,
            body = "OK",
            contentType = "text/plain",
            verboseLogging = true,
            logger = recordingLogger(lines),
        )

        client.submitForm(
            url = "https://exemple.org/api/greader.php/reader/api/0/edit-tag",
            formParameters = parameters {
                append("a", "user/-/state/com.google/read")
                append("i", "1743158400000042")
            },
        ).bodyAsText()

        val logged = lines.joinToString("\n")
        assertTrue("1743158400000042" in logged, "les identifiants envoyés doivent être lisibles dans les journaux")
    }

    @Test
    fun nothingIsLoggedWhenVerboseLoggingIsOff() = runTest {
        // The configuration of release builds: even redacted, logging would
        // expose the personal server address.
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
        // FreshRSS adds fields across versions. Without this tolerance, a
        // server update would break every read.
        val decoded = FreshRssJson.decodeFromString(
            ProbeResponse.serializer(),
            """{"id":"feed/1","champInconnuAjouteParUneFutureVersion":42}""",
        )

        assertEquals("feed/1", decoded.id)
    }

    @kotlinx.serialization.Serializable
    private data class ProbeResponse(val id: String)
}
