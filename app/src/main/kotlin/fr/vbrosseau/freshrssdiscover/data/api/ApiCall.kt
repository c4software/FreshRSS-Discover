package fr.vbrosseau.freshrssdiscover.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/** Scheme of the `Authorization` header every authenticated call carries. */
internal const val AUTHORIZATION_SCHEME = "GoogleLogin auth="

/**
 * Maps every transport exception to [ApiOutcome.TransportError].
 *
 * `CancellationException` must keep propagating: catching it would let a
 * coroutine outlive the cancellation of its scope, while the screen awaiting
 * it is already gone.
 */
internal suspend fun <T> apiCall(block: suspend () -> ApiOutcome<T>): ApiOutcome<T> = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
    ApiOutcome.TransportError(failure)
}

/**
 * FreshRSS error bodies are plain text; an unreadable `2xx` is therefore a
 * malformed body, not a transport failure.
 */
internal fun <T> decodeJson(serializer: KSerializer<T>, body: String, endpoint: String): ApiOutcome<T> = try {
    ApiOutcome.Success(FreshRssJson.decodeFromString(serializer, body))
} catch (@Suppress("SwallowedException") failure: SerializationException) {
    ApiOutcome.MalformedResponse("réponse de $endpoint illisible : ${failure.message}")
}
