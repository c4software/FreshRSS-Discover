package fr.vbrosseau.freshrssdiscover.data.api

import fr.vbrosseau.freshrssdiscover.domain.auth.AuthToken
import fr.vbrosseau.freshrssdiscover.domain.auth.ServerAddress
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The subscription endpoints of the FreshRSS API (docs/freshrss-api.md §3.1
 * and §4.3): list, subscribe, unsubscribe.
 *
 * Same rules as [FreshRssApi]: nothing FreshRSS-specific leaves this layer,
 * and no method throws.
 */
@Singleton
internal class FreshRssSubscriptionApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    /** `output=json` is mandatory: any other value answers `501`. */
    suspend fun list(address: ServerAddress, token: AuthToken): ApiOutcome<SubscriptionListDto> = apiCall {
        val response = httpClient.get(address.apiEndpoint + LIST_PATH) {
            header(HttpHeaders.Authorization, "$AUTHORIZATION_SCHEME${token.value}")
            parameter(PARAM_OUTPUT, OUTPUT_JSON)
        }
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, response.bodyAsText())
            else -> decodeJson(SubscriptionListDto.serializer(), response.bodyAsText(), "subscription/list")
        }
    }

    /**
     * Subscribes to a feed by its address.
     *
     * The stream name carries the URL itself, prefixed with `feed/`: the
     * server fetches it, and a `400` is its only way of saying the address is
     * not a feed. Unlike `edit-tag`, no modification token is checked on this
     * path — read in the dispatcher's source, not assumed.
     */
    suspend fun subscribe(address: ServerAddress, token: AuthToken, feedUrl: String): ApiOutcome<Unit> =
        edit(address, token, streamName = STREAM_PREFIX + feedUrl, action = ACTION_SUBSCRIBE)

    /** Removes a subscription by its `feed/<id>` stream name, as listed. */
    suspend fun unsubscribe(address: ServerAddress, token: AuthToken, streamId: String): ApiOutcome<Unit> =
        edit(address, token, streamName = streamId, action = ACTION_UNSUBSCRIBE)

    private suspend fun edit(
        address: ServerAddress,
        token: AuthToken,
        streamName: String,
        action: String,
    ): ApiOutcome<Unit> = apiCall {
        val response = httpClient.submitForm(
            url = address.apiEndpoint + EDIT_PATH,
            formParameters = parameters {
                append(PARAM_STREAM, streamName)
                append(PARAM_ACTION, action)
            },
        ) {
            header(HttpHeaders.Authorization, "$AUTHORIZATION_SCHEME${token.value}")
        }
        val body = response.bodyAsText().trim()
        when {
            !response.status.isSuccess() -> ApiOutcome.HttpError(response.status.value, body)
            body == EDIT_RESPONSE -> ApiOutcome.Success(Unit)
            else -> ApiOutcome.MalformedResponse("subscription/edit n'a pas répondu « $EDIT_RESPONSE »")
        }
    }

    private companion object {
        const val LIST_PATH = "/reader/api/0/subscription/list"
        const val EDIT_PATH = "/reader/api/0/subscription/edit"
        const val PARAM_OUTPUT = "output"
        const val OUTPUT_JSON = "json"
        const val PARAM_STREAM = "s"
        const val PARAM_ACTION = "ac"
        const val ACTION_SUBSCRIBE = "subscribe"
        const val ACTION_UNSUBSCRIBE = "unsubscribe"
        const val STREAM_PREFIX = "feed/"
        const val EDIT_RESPONSE = "OK"
    }
}
