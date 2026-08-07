package fr.vbrosseau.freshrssdiscover.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Délai au-delà duquel un serveur est tenu pour injoignable. */
private const val REQUEST_TIMEOUT_MILLIS = 30_000L

/**
 * Établir la connexion doit échouer plus vite que la traiter : un serveur
 * éteint se manifeste ici, et faire patienter l'utilisateur trente secondes
 * pour l'apprendre serait inutilement long.
 */
private const val CONNECT_TIMEOUT_MILLIS = 10_000L

/**
 * Sérialiseur commun aux réponses de l'API.
 *
 * `ignoreUnknownKeys` : FreshRSS ajoute des champs au fil des versions —
 * `frss:priority` en est un exemple récent. Sans cette tolérance, une mise à
 * jour du serveur suffirait à faire échouer la lecture de toutes les réponses.
 */
internal val FreshRssJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

/**
 * Construit le client HTTP de l'API FreshRSS.
 *
 * Le moteur est un paramètre plutôt qu'un choix interne : c'est ce qui permet
 * aux tests d'y substituer un `MockEngine` et de décrire des réponses
 * littérales, malformées comprises.
 *
 * @param verboseLogging à n'activer qu'en construction de débogage.
 */
internal fun createFreshRssHttpClient(
    engine: HttpClientEngine,
    verboseLogging: Boolean = false,
    logger: Logger = TimberLogger,
): HttpClient = HttpClient(engine) {
    /**
     * Aucune exception sur un statut d'erreur : les codes de FreshRSS *sont*
     * l'information utile — `503` signifie « API désactivée », `401` « jeton
     * invalide » (docs/freshrss-api.md §5). Les laisser lever obligerait à
     * reconstituer le code depuis une exception, et à traiter les erreurs
     * réseau et applicatives par le même canal.
     */
    expectSuccess = false

    install(ContentNegotiation) {
        /*
         * Enregistré pour `application/json` uniquement — c'est le défaut, et
         * il est ici essentiel : les réponses d'erreur de FreshRSS sont en
         * `text/plain`. Un enregistrement plus large tenterait de les
         * désérialiser, échouerait, et masquerait le code HTTP réel.
         */
        json(FreshRssJson)
    }

    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
    }

    if (verboseLogging) {
        install(Logging) {
            this.logger = logger

            // HEADERS et non BODY : le corps de `ClientLogin` contient le mot
            // de passe API en clair. Les en-têtes, eux, servent réellement au
            // diagnostic — encore faut-il masquer celui qui porte le jeton.
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
    }
}

/** Renvoie la journalisation de Ktor vers celle du reste de l'application. */
internal object TimberLogger : Logger {
    override fun log(message: String) {
        Timber.tag("FreshRssApi").d(message)
    }
}
