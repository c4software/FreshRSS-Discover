package fr.vbrosseau.freshrssdiscover.domain.auth

import java.net.URI

/**
 * Adresse d'une instance FreshRSS, sous forme normalisée.
 *
 * L'utilisateur saisit son serveur tel qu'il le connaît — `rss.exemple.org`,
 * `https://exemple.org/freshrss/`, parfois l'URL complète de l'API qu'il a
 * trouvée dans une documentation. Lui demander une forme canonique reviendrait
 * à lui faire porter un détail d'implémentation (SPECS.md §3.1).
 *
 * Le type ne s'obtient que par [parse] : une instance existe donc toujours sous
 * forme valide et normalisée, et aucune couche supérieure n'a à revalider.
 */
class ServerAddress private constructor(
    /** Racine de l'instance, sans barre oblique finale. Ex. `https://exemple.org/freshrss`. */
    val baseUrl: String,
    /**
     * `false` pour une instance servie en clair.
     *
     * `http://` reste accepté — les instances auto-hébergées sur réseau local
     * sont un cas réel — mais l'interface doit le signaler (SPECS.md §3.1).
     */
    val isSecure: Boolean,
) {
    /**
     * Point d'entrée de l'API compatible Google Reader.
     *
     * Dérivé, jamais saisi : c'est ce qui permet à l'utilisateur d'ignorer
     * l'existence de `greader.php`.
     */
    val apiEndpoint: String get() = baseUrl + API_PATH

    override fun toString(): String = "ServerAddress($baseUrl)"

    override fun equals(other: Any?): Boolean = this === other || (other is ServerAddress && baseUrl == other.baseUrl)

    override fun hashCode(): Int = baseUrl.hashCode()

    companion object {
        private const val API_PATH = "/api/greader.php"
        private const val HTTP = "http"
        private const val HTTPS = "https"
        private val SUPPORTED_SCHEMES = setOf(HTTP, HTTPS)

        private const val DEFAULT_HTTPS_PORT = 443
        private const val DEFAULT_HTTP_PORT = 80

        /** `URI.getPort()` vaut ceci lorsque l'adresse ne précise pas de port. */
        private const val NO_PORT = -1

        /** Reconnaît un schéma déjà présent : `https://`, `http://`, `ftp://`… */
        private val EXPLICIT_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

        /**
         * Normalise une adresse saisie.
         *
         * Traite, dans cet ordre : espaces superflus, schéma implicite,
         * majuscules de l'hôte, barres obliques finales, et suffixe
         * `/api/greader.php` que l'utilisateur aurait recopié.
         */
        fun parse(raw: String): ServerAddressResult {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) ServerAddressResult.Blank else parseNonBlank(trimmed)
        }

        private fun parseNonBlank(input: String): ServerAddressResult {
            // `URI` refuse une entrée sans schéma : sans ce préfixe, `host`
            // serait null pour la saisie la plus courante — un simple nom de
            // domaine.
            val candidate = if (EXPLICIT_SCHEME.containsMatchIn(input)) input else "$HTTPS://$input"
            val uri = runCatching { URI(candidate) }.getOrNull()
            val scheme = uri?.scheme?.lowercase()
            val host = uri?.host?.lowercase()

            return when {
                uri == null || scheme == null -> ServerAddressResult.Malformed
                scheme !in SUPPORTED_SCHEMES -> ServerAddressResult.UnsupportedScheme(scheme)
                host.isNullOrEmpty() -> ServerAddressResult.Malformed
                else ->
                    ServerAddressResult.Valid(
                        ServerAddress(
                            baseUrl = buildBaseUrl(scheme, host, uri),
                            isSecure = scheme == HTTPS,
                        ),
                    )
            }
        }

        private fun buildBaseUrl(
            scheme: String,
            host: String,
            uri: URI,
        ): String {
            // Le port par défaut du schéma est omis : `https://exemple.org` et
            // `https://exemple.org:443` désignent la même instance, et deux
            // formes distinctes produiraient deux sessions distinctes.
            val port =
                when (uri.port) {
                    NO_PORT, defaultPortOf(scheme) -> ""
                    else -> ":${uri.port}"
                }
            return scheme + "://" + host + port + normalizePath(uri.path.orEmpty())
        }

        private fun defaultPortOf(scheme: String): Int = if (scheme == HTTPS) DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT

        /**
         * Retire les barres obliques finales et le suffixe de l'API.
         *
         * Recopier l'URL complète de l'API est un geste naturel : elle figure
         * dans la documentation de FreshRSS et dans la configuration des autres
         * clients. La refuser serait gratuitement hostile.
         */
        private fun normalizePath(path: String): String {
            val withoutTrailingSlashes = path.trimEnd('/')
            val withoutApiPath = withoutTrailingSlashes.removeSuffix(API_PATH)
            return withoutApiPath.trimEnd('/')
        }
    }
}

/** Issue de [ServerAddress.parse]. */
sealed interface ServerAddressResult {
    data class Valid(val address: ServerAddress) : ServerAddressResult

    /** Rien n'a été saisi. */
    data object Blank : ServerAddressResult

    /** Aucun hôte exploitable n'a pu être extrait. */
    data object Malformed : ServerAddressResult

    /** Schéma autre que `http` ou `https` — `ftp://`, `file://`… */
    data class UnsupportedScheme(val scheme: String) : ServerAddressResult
}
