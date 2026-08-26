package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssSubscriptionApi
import fr.vbrosseau.freshrssdiscover.data.api.HTTP_UNAUTHORIZED
import fr.vbrosseau.freshrssdiscover.data.api.SubscriptionDto
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.di.IoDispatcher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.core.Outcome
import fr.vbrosseau.freshrssdiscover.domain.subscription.FeedUrl
import fr.vbrosseau.freshrssdiscover.domain.subscription.Subscription
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionError
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionId
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionRepository
import fr.vbrosseau.freshrssdiscover.domain.subscription.SubscriptionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The server understood and refused (docs/freshrss-api.md §4.3). */
private const val HTTP_BAD_REQUEST = 400

/** Stream-name prefix of a feed, the form `subscription/list` returns and `unsubscribe` expects. */
private const val STREAM_PREFIX = "feed/"

/**
 * Subscriptions straight from the server, without any local copy.
 *
 * Each call reads the session first: with none, there is nobody to list
 * for, and the answer is [SubscriptionError.SessionExpired] — the same
 * word the root router acts on.
 */
@Singleton
internal class DefaultSubscriptionRepository @Inject constructor(
    private val api: FreshRssSubscriptionApi,
    private val sessionStore: SessionStore,
    private val network: NetworkAvailability,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SubscriptionRepository {
    override suspend fun list(): SubscriptionResult<List<Subscription>> = withSession { session ->
        api.list(session.server, session.token).toResult { dto ->
            dto.subscriptions.mapNotNull(SubscriptionDto::toDomain)
        }
    }

    override suspend fun subscribe(url: FeedUrl): SubscriptionResult<Unit> = withSession { session ->
        api.subscribe(session.server, session.token, url.value).toResult { }
    }

    override suspend fun unsubscribe(id: SubscriptionId): SubscriptionResult<Unit> = withSession { session ->
        api.unsubscribe(session.server, session.token, STREAM_PREFIX + id.value).toResult { }
    }

    private suspend fun <T> withSession(
        block: suspend (AuthSession) -> SubscriptionResult<T>,
    ): SubscriptionResult<T> = withContext(ioDispatcher) {
        val session = sessionStore.observeSession().first()
        if (session == null) Outcome.Failure(SubscriptionError.SessionExpired) else block(session)
    }

    /**
     * Same reading of a `401` as the other repositories: the session is
     * wiped here, and the root router switches on its own (SPECS.md §3.4).
     * Connectivity is read at the moment of failure, not before: the
     * network can vanish during the request, which is the case to diagnose.
     */
    private suspend fun <T, R> ApiOutcome<T>.toResult(onSuccess: (T) -> R): SubscriptionResult<R> = when (this) {
        is ApiOutcome.Success -> Outcome.Success(onSuccess(value))
        is ApiOutcome.HttpError -> Outcome.Failure(httpFailure(status))
        is ApiOutcome.MalformedResponse -> Outcome.Failure(SubscriptionError.Unexpected(detail))
        is ApiOutcome.TransportError -> Outcome.Failure(
            if (network.isOnline()) SubscriptionError.ServerUnreachable else SubscriptionError.NoNetwork,
        )
    }

    private suspend fun httpFailure(status: Int): SubscriptionError = when (status) {
        HTTP_UNAUTHORIZED -> {
            sessionStore.invalidateTokens()
            SubscriptionError.SessionExpired
        }

        HTTP_BAD_REQUEST -> SubscriptionError.Rejected
        else -> SubscriptionError.Unexpected("HTTP $status")
    }
}

/**
 * A stream name that is not `feed/<decimal>` is dropped rather than
 * failing the whole list: the survey only ever observed that shape, and
 * one odd entry must not hide the others. Removal needs the number.
 */
private fun SubscriptionDto.toDomain(): Subscription? =
    id.removePrefix(STREAM_PREFIX).toLongOrNull()?.let { number ->
        Subscription(id = SubscriptionId(number), title = title, url = url)
    }
