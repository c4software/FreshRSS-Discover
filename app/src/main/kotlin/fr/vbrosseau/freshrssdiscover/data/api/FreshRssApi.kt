package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.Credentials
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import fr.vbrosseau.freshrssdiscover.domain.feed.PageCursor
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point de contact unique avec l'API compatible Google Reader de FreshRSS.
 *
 * Tout ce qui est propre à cette API — chemins, en-têtes, forme des réponses,
 * jetons — s'arrête ici (ARCHITECTURE.md §2.1). Rien de tout cela ne doit
 * apparaître au-dessus.
 *
 * Aucune méthode ne lève : les défaillances sont rapportées par [ApiOutcome].
 * Une exception qui traverserait cette couche obligerait chaque appelant à
 * connaître les exceptions de Ktor.
 */
@Singleton
internal class FreshRssApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    /**
     * Vérifie que l'adresse désigne bien une instance FreshRSS.
     *
     * Le seul discriminant fiable est le corps `OK` renvoyé par un `GET` nu sur
     * le point d'entrée (docs/freshrss-api.md §1.1). Deux pièges constatés : le
     * `Content-Type` est `text/html`, et **la moindre chaîne de requête** fait
     * répondre `400` au lieu de `OK`.
     *
     * Cette sonde se passe avant toute tentative de connexion : sans elle, une
     * faute de frappe dans l'adresse produirait un `401` que l'utilisateur
     * imputerait à son mot de passe.
     */
    suspend fun probe(address: ServerAddress): ApiOutcome<Unit> = call {
        val response = httpClient.get(address.apiEndpoint)
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            response.bodyAsText().trim() == PROBE_RESPONSE -> ApiOutcome.Success(Unit)
            else -> ApiOutcome.MalformedResponse("la racine de l'API n'a pas répondu « $PROBE_RESPONSE »")
        }
    }

    /**
     * Vérifie que le serveur web transmet bien l'en-tête `Authorization`.
     *
     * Certains reverse-proxies le suppriment ; toute connexion échouerait alors
     * en `401`, accusant à tort les identifiants de l'utilisateur.
     *
     * Deux particularités constatées, sans lesquelles la sonde ne vaut rien :
     * le statut est **toujours `200`** — le verdict est dans le corps — et la
     * requête doit elle-même porter un en-tête `Authorization`, fût-il factice,
     * puisque c'est sa présence en réception qui est constatée.
     */
    suspend fun checkAuthorizationForwarding(address: ServerAddress): ApiOutcome<Boolean> = call {
        val response = httpClient.get(address.apiEndpoint + COMPATIBILITY_PATH) {
            header(HttpHeaders.Authorization, "GoogleLogin auth=$COMPATIBILITY_PROBE_TOKEN")
        }
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            else -> ApiOutcome.Success(response.bodyAsText().trim().startsWith(COMPATIBILITY_PASS))
        }
    }

    /**
     * Ouvre une session.
     *
     * Le mot de passe attendu est le **mot de passe API**, distinct de celui de
     * connexion. Il part en `POST` : FreshRSS accepte aussi la méthode `GET`,
     * mais journalise alors un avertissement — le mot de passe apparaîtrait
     * dans les journaux du serveur.
     *
     * La réponse est du texte brut, une paire `clé=valeur` par ligne. Seule
     * `Auth` est retenue ; `SID` porte la même valeur et `LSID` vaut `null`.
     */
    suspend fun clientLogin(address: ServerAddress, credentials: Credentials): ApiOutcome<AuthToken> = call {
        val response = httpClient.submitForm(
            url = address.apiEndpoint + CLIENT_LOGIN_PATH,
            formParameters = parameters {
                append("Email", credentials.username)
                append("Passwd", credentials.apiPassword)
            },
        )
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            else -> tokenFrom(response)
        }
    }

    /**
     * Récupère une page du flux de lecture.
     *
     * Trois précautions, chacune tirée d'un constat (docs/freshrss-api.md §3.4
     * et §3.5) :
     *
     * - l'en-tête `Authorization` est **obligatoire** ici, contrairement à
     *   `ClientLogin` : sans lui le serveur répond `401` ;
     * - le paramètre `c` n'est ajouté **que** si un curseur existe. Un `c` vide
     *   ou non numérique est silencieusement ramené au début du flux : la
     *   requête réussit, et renvoie à nouveau la première page. Envoyer un `c`
     *   vide produirait donc une boucle infinie muette, jamais une erreur ;
     * - le corps est désérialisé à la main plutôt que par `response.body()` :
     *   un JSON tronqué doit devenir [ApiOutcome.MalformedResponse], pas une
     *   exception que chaque appelant devrait rattraper.
     *
     * @param unreadOnly exclut les articles déjà lus, via `xt`.
     */
    suspend fun streamContents(
        address: ServerAddress,
        token: AuthToken,
        pageSize: Int,
        cursor: PageCursor? = null,
        unreadOnly: Boolean = true,
    ): ApiOutcome<StreamContentsDto> = call {
        val response = httpClient.get(address.apiEndpoint + STREAM_CONTENTS_PATH) {
            header(HttpHeaders.Authorization, "$AUTHORIZATION_SCHEME${token.value}")
            parameter(PARAM_COUNT, pageSize)
            cursor?.let { parameter(PARAM_CONTINUATION, it.value) }
            if (unreadOnly) parameter(PARAM_EXCLUDE_TARGET, READ_STATE)
        }
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            else -> streamContentsFrom(response.bodyAsText())
        }
    }

    /**
     * Les corps d'erreur de FreshRSS sont en texte brut ; un `2xx` illisible
     * relève donc du corps malformé, pas du transport.
     */
    private fun streamContentsFrom(body: String): ApiOutcome<StreamContentsDto> = try {
        ApiOutcome.Success(FreshRssJson.decodeFromString(StreamContentsDto.serializer(), body))
    } catch (@Suppress("SwallowedException") failure: SerializationException) {
        ApiOutcome.MalformedResponse("réponse de stream/contents illisible : ${failure.message}")
    }

    private suspend fun tokenFrom(response: HttpResponse): ApiOutcome<AuthToken> {
        val auth = response.bodyAsText()
            .lineSequence()
            .mapNotNull { line -> line.trim().takeIf { it.startsWith(AUTH_PREFIX) } }
            .map { it.removePrefix(AUTH_PREFIX).trim() }
            .firstOrNull { it.isNotEmpty() }

        return when (auth) {
            null -> ApiOutcome.MalformedResponse("aucune ligne « ${AUTH_PREFIX}… » dans la réponse")
            else -> ApiOutcome.Success(AuthToken(auth))
        }
    }

    /**
     * Rabat toute exception de transport sur [ApiOutcome.TransportError].
     *
     * `CancellationException` doit en revanche continuer à remonter : la
     * rattraper ferait survivre une coroutine à l'annulation de sa portée, et
     * l'écran qui l'attend a déjà disparu.
     */
    private suspend fun <T> call(block: suspend () -> ApiOutcome<T>): ApiOutcome<T> = try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        ApiOutcome.TransportError(failure)
    }

    private companion object {
        const val CLIENT_LOGIN_PATH = "/accounts/ClientLogin"
        const val COMPATIBILITY_PATH = "/check/compatibility"
        const val STREAM_CONTENTS_PATH = "/reader/api/0/stream/contents/reading-list"
        const val AUTH_PREFIX = "Auth="
        const val AUTHORIZATION_SCHEME = "GoogleLogin auth="
        const val PARAM_COUNT = "n"
        const val PARAM_CONTINUATION = "c"
        const val PARAM_EXCLUDE_TARGET = "xt"

        /** Seul état utile ici : `xt` l'exclut, ce qui ne laisse que les non-lus. */
        const val READ_STATE = "user/-/state/com.google/read"
        const val PROBE_RESPONSE = "OK"
        const val COMPATIBILITY_PASS = "PASS"

        /**
         * Jeton factice de la sonde de compatibilité : elle constate la
         * *présence* de l'en-tête, jamais sa validité.
         */
        const val COMPATIBILITY_PROBE_TOKEN = "x/y"
    }
}
