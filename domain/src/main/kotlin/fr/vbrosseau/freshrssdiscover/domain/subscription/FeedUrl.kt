package fr.vbrosseau.freshrssdiscover.domain.subscription

import java.net.URI
import java.net.URISyntaxException

/**
 * Address of a feed, as it may leave the device.
 *
 * Only obtainable through [parse]: the server answers a bare `400` to any
 * address it cannot fetch, with no word on why (docs/freshrss-api.md §4.3),
 * so what can be told before sending — an empty field, a missing host, a
 * scheme the server will not fetch — is told here, where the message can
 * name the cause.
 *
 * Normalisation stays light on purpose: `https://` is assumed when the user
 * typed none, as on the sign-in screen (SPECS.md §3.1), and nothing else is
 * touched. The feed's URL is the server's key for the subscription; rewriting
 * it would make the listed address differ from the typed one.
 */
class FeedUrl private constructor(val value: String) {
    override fun toString(): String = "FeedUrl($value)"

    override fun equals(other: Any?): Boolean = this === other || (other is FeedUrl && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    companion object {
        private val SUPPORTED_SCHEMES = setOf("http", "https")

        /** Matches an explicit scheme: `https://`, `feed://`, `ftp://`, etc. */
        private val EXPLICIT_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

        fun parse(raw: String): FeedUrlResult {
            val trimmed = raw.trim()
            val withScheme = if (EXPLICIT_SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
            val uri = uriOrNull(withScheme)

            return when {
                trimmed.isEmpty() -> FeedUrlResult.Blank
                uri == null -> FeedUrlResult.Invalid
                uri.scheme?.lowercase() !in SUPPORTED_SCHEMES -> FeedUrlResult.Invalid
                uri.host.isNullOrEmpty() -> FeedUrlResult.Invalid
                else -> FeedUrlResult.Valid(FeedUrl(withScheme))
            }
        }

        private fun uriOrNull(value: String): URI? =
            try {
                URI(value)
            } catch (
                @Suppress("SwallowedException") malformed: URISyntaxException,
            ) {
                null
            }
    }
}

/** Result of [FeedUrl.parse]: the two refusals are worded differently on screen. */
sealed interface FeedUrlResult {
    data class Valid(val url: FeedUrl) : FeedUrlResult

    /** Nothing typed: not an error to shout about, just nothing to send. */
    data object Blank : FeedUrlResult

    /** Typed, but not an address the server could fetch. */
    data object Invalid : FeedUrlResult
}
