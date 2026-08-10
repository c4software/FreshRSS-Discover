package fr.vbrosseau.freshrssdiscover.domain.auth

import java.net.URI

/**
 * Address of a FreshRSS instance, in normalized form.
 *
 * Users type their server as they know it: `rss.exemple.org`,
 * `https://exemple.org/freshrss/`, sometimes the full API URL found in some
 * documentation. Requiring a canonical form would push an implementation
 * detail onto the user (SPECS.md §3.1).
 *
 * The type can only be obtained through [parse]: an instance always exists in
 * valid, normalized form, so no upper layer needs to revalidate.
 */
class ServerAddress private constructor(
    /** Instance root, without a trailing slash. E.g. `https://exemple.org/freshrss`. */
    val baseUrl: String,
    /**
     * `false` for an instance served over plain HTTP.
     *
     * `http://` remains accepted, since self-hosted instances on a local
     * network are a real case, but the UI must flag it (SPECS.md §3.1).
     */
    val isSecure: Boolean,
) {
    /**
     * Endpoint of the Google Reader-compatible API.
     *
     * Derived, never typed: this lets the user ignore the existence of
     * `greader.php`.
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

        /** `URI.getPort()` returns this when the address specifies no port. */
        private const val NO_PORT = -1

        /** Matches an explicit scheme: `https://`, `http://`, `ftp://`, etc. */
        private val EXPLICIT_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

        /**
         * Normalizes a typed address.
         *
         * Handles, in this order: surrounding whitespace, implicit scheme,
         * uppercase host, trailing slashes, and a copied `/api/greader.php`
         * suffix.
         */
        fun parse(raw: String): ServerAddressResult {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) ServerAddressResult.Blank else parseNonBlank(trimmed)
        }

        private fun parseNonBlank(input: String): ServerAddressResult {
            // `URI` rejects input without a scheme: without this prefix, `host`
            // would be null for the most common input, a bare domain name.
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
            // The scheme's default port is omitted: `https://exemple.org` and
            // `https://exemple.org:443` name the same instance, and two
            // distinct forms would produce two distinct sessions.
            val port =
                when (uri.port) {
                    NO_PORT, defaultPortOf(scheme) -> ""
                    else -> ":${uri.port}"
                }
            return scheme + "://" + host + port + normalizePath(uri.path.orEmpty())
        }

        private fun defaultPortOf(scheme: String): Int = if (scheme == HTTPS) DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT

        /**
         * Strips trailing slashes and the API suffix.
         *
         * Pasting the full API URL is natural: it appears in the FreshRSS
         * documentation and in other clients' configuration, so it is
         * accepted.
         */
        private fun normalizePath(path: String): String {
            val withoutTrailingSlashes = path.trimEnd('/')
            val withoutApiPath = withoutTrailingSlashes.removeSuffix(API_PATH)
            return withoutApiPath.trimEnd('/')
        }
    }
}

/** Result of [ServerAddress.parse]. */
sealed interface ServerAddressResult {
    data class Valid(val address: ServerAddress) : ServerAddressResult

    /** Nothing was entered. */
    data object Blank : ServerAddressResult

    /** No usable host could be extracted. */
    data object Malformed : ServerAddressResult

    /** Scheme other than `http` or `https`, e.g. `ftp://` or `file://`. */
    data class UnsupportedScheme(val scheme: String) : ServerAddressResult
}
