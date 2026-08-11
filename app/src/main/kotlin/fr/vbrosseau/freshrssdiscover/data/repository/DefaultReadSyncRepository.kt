package fr.vbrosseau.freshrssdiscover.data.repository

import fr.vbrosseau.freshrssdiscover.READ_SYNC_TAG
import fr.vbrosseau.freshrssdiscover.data.api.ApiOutcome
import fr.vbrosseau.freshrssdiscover.data.api.FreshRssApi
import fr.vbrosseau.freshrssdiscover.data.api.HTTP_UNAUTHORIZED
import fr.vbrosseau.freshrssdiscover.data.local.SessionStore
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleCache
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkQueue
import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.di.IoDispatcher
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthSession
import fr.vbrosseau.freshrssdiscover.domain.auth.ModificationToken
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadTransmissionScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Number of articles transmitted per `edit-tag` request.
 *
 * Two bounds, and the value is chosen between them:
 *
 * - lower bound: a feed page is 40 articles, so a smaller batch would make
 *   several requests for a single page read — what batching exists precisely
 *   to avoid (SPECS.md §4.5);
 * - upper bound: each article is an `i` form field, and PHP by default
 *   accepts only 1,000 fields per request (`max_input_vars`). Beyond that,
 *   excess fields are silently dropped — and `edit-tag` answers `OK` with no
 *   per-article report (docs/freshrss-api.md §4.1). The loss would be
 *   completely silent.
 *
 * 100 stays an order of magnitude under that limit, covers more than two
 * pages of reading in a single request, and weighs about 2 KB of body.
 */
private const val BATCH_SIZE = 100

/**
 * Optimistic marking, and batched transmission of what is pending.
 *
 * The role split is that of SPECS.md §4.5: `ArticleCache` holds the local
 * read state, `PendingMarkQueue` holds what remains to tell the server. Both
 * writes happen together when an article is read, and nothing else links them
 * — which is what lets transmission fail without reading noticing.
 */
@Singleton
internal class DefaultReadSyncRepository @Inject constructor(
    private val api: FreshRssApi,
    private val sessionStore: SessionStore,
    private val articleCache: ArticleCache,
    private val queue: PendingMarkQueue,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope applicationScope: CoroutineScope,
) : ReadSyncRepository {
    /**
     * Groups transmissions over time (SPECS.md §4.5).
     *
     * The scope is the application's, not the caller's: a window opened during
     * reading must survive the disappearance of the screen that opened it,
     * otherwise the mark would wait for the next launch to leave.
     *
     * The queue is consulted before the session: with nothing to transmit,
     * there is no request to make and no session to require. This is the
     * common startup case, where `flush()` is called without knowing whether
     * anything remains.
     */
    private val scheduler = ReadTransmissionScheduler(scope = applicationScope) {
        withContext(ioDispatcher) {
            val session = sessionStore.observeSession().first()
            if (queue.pending(limit = 1).isNotEmpty() && session != null) transmit(session)
        }
    }

    /**
     * Flips the local state, enqueues, then opens the grouping window. The
     * order matters twice:
     *
     * - local state first, because it is what the user sees and the queue is
     *   only a means. If the process were killed between the two, the article
     *   would stay read on screen — a harmless outcome — whereas the reverse
     *   order would leave a queued mark for an article displayed as unread;
     * - the window last, because this order guarantees an already open window
     *   will carry what was just enqueued.
     *
     * Nothing here waits for the network: the window opens and this call
     * returns.
     */
    override suspend fun markAsRead(ids: Set<ArticleId>) = withContext(ioDispatcher) {
        if (ids.isNotEmpty()) {
            val ordered = ids.toList()
            articleCache.markAsRead(ordered)
            queue.enqueue(ordered)
            Timber.tag(READ_SYNC_TAG).d("mise en file : %s", ordered.map(ArticleId::value))
            scheduler.schedule()
        }
    }

    /**
     * Forces the transmission without waiting for the current window.
     *
     * This is the meaning `flush()` already had — the startup replay — and
     * grouping does not change it: what is deferred is ordinary marking, not
     * an explicit request to send.
     */
    override suspend fun flush() = scheduler.transmitNow()

    /**
     * The current window is abandoned along with the queue: after a logout it
     * would have nothing left to tell the server.
     */
    override suspend fun clearPending() = withContext(ioDispatcher) {
        scheduler.cancelScheduled()
        queue.clear()
    }

    /**
     * Drains the queue batch by batch, acknowledging each confirmed batch.
     *
     * The queue is re-read on each iteration rather than split upfront: a
     * mark can be added during transmission, and partial acknowledgement must
     * remain the only thing that removes rows.
     *
     * The obtained modification token serves all subsequent batches —
     * re-requesting it per request would double the round trips for nothing,
     * as it is deterministic and reusable (docs/freshrss-api.md §2.3).
     */
    private suspend fun transmit(session: AuthSession) {
        var token = session.modificationToken
        var done = false

        while (!done) {
            val batch = queue.pending(BATCH_SIZE)
            if (batch.isEmpty()) {
                done = true
                continue
            }
            /*
             * The outcome is the trace that matters most on this path: it is
             * the only place where `Sent` (queue acknowledged) and `Deferred`
             * (queue kept, replayed later) part ways, and nothing outside this
             * loop can tell them apart. A run of `Deferred` says the marks are
             * piling up; a `Sent` on articles the server still returns as
             * unread says `edit-tag` accepted and ignored them.
             */
            val outcome = sendBatch(session, batch, token)
            Timber.tag(READ_SYNC_TAG).d(
                "lot de %d %s : %s",
                batch.size,
                batch.map(ArticleId::value),
                outcome.javaClass.simpleName,
            )

            when (outcome) {
                is BatchOutcome.Sent -> {
                    // Acknowledgement follows confirmation, never the send: a
                    // row removed too early would be a lost article.
                    queue.acknowledge(batch)
                    token = outcome.token
                }

                /*
                 * Only the tokens fall, and the root router returns to the
                 * sign-in screen on its own, prefilled (SPECS.md §3.4). The
                 * queue is left intact: these marks must survive the
                 * reconnection, otherwise the user would see as unread what
                 * they read before the expiry.
                 */
                BatchOutcome.SessionLost -> {
                    sessionStore.invalidateTokens()
                    done = true
                }

                // Nothing was sent, nothing is lost: the queue keeps the rows
                // and the next pass will retry (SPECS.md §4.5).
                BatchOutcome.Deferred -> done = true
            }
        }
    }

    /**
     * Sends a batch with the known token, and only requests a new one on `401`.
     *
     * The known token may come from a previous launch: it is stored with the
     * session. Assuming it valid is the right bet — it is deterministic — and
     * the `401` is the only reliable signal of its invalidation.
     */
    private suspend fun sendBatch(
        session: AuthSession,
        batch: List<ArticleId>,
        knownToken: ModificationToken?,
    ): BatchOutcome {
        val token = knownToken ?: return renewThenSend(session, batch)
        return when (val sent = api.markAsRead(session.server, session.token, token, batch)) {
            is ApiOutcome.Success -> BatchOutcome.Sent(token)
            is ApiOutcome.HttpError ->
                if (sent.status == HTTP_UNAUTHORIZED) renewThenSend(session, batch) else BatchOutcome.Deferred

            else -> BatchOutcome.Deferred
        }
    }

    /**
     * Re-requests the modification token, then retries exactly once.
     *
     * This is the behavior prescribed by docs/freshrss-api.md §2.3. Looping
     * further would gain nothing: a freshly obtained token that is already
     * rejected no longer indicts the token but the session itself.
     */
    private suspend fun renewThenSend(session: AuthSession, batch: List<ArticleId>): BatchOutcome =
        when (val renewed = requestModificationToken(session)) {
            is ApiOutcome.Success -> sendWithRenewedToken(session, batch, renewed.value)
            is ApiOutcome.HttpError -> unauthorizedOrDeferred(renewed.status)
            else -> BatchOutcome.Deferred
        }

    /** Last attempt: a `401` here can no longer be blamed on the token. */
    private suspend fun sendWithRenewedToken(
        session: AuthSession,
        batch: List<ArticleId>,
        token: ModificationToken,
    ): BatchOutcome =
        when (val sent = api.markAsRead(session.server, session.token, token, batch)) {
            is ApiOutcome.Success -> BatchOutcome.Sent(token)
            is ApiOutcome.HttpError -> unauthorizedOrDeferred(sent.status)
            else -> BatchOutcome.Deferred
        }

    /**
     * The obtained token is stored with the session: it survives a restart,
     * and the replay at the next launch goes straight to `edit-tag`
     * (docs/freshrss-api.md §2.3).
     */
    private suspend fun requestModificationToken(session: AuthSession): ApiOutcome<ModificationToken> {
        val outcome = api.modificationToken(session.server, session.token)
        if (outcome is ApiOutcome.Success) {
            sessionStore.save(session.copy(modificationToken = outcome.value))
        }
        return outcome
    }

    /**
     * A status other than `401` is a server incident, not a rejection: it is
     * deferred, like a network outage. Nothing is removed from the queue.
     */
    private fun unauthorizedOrDeferred(status: Int): BatchOutcome =
        if (status == HTTP_UNAUTHORIZED) BatchOutcome.SessionLost else BatchOutcome.Deferred
}

/**
 * Fate of a batch, internal to the transmission loop.
 *
 * One outcome per request, not per whole queue: concluding the transmission
 * loop on the first batch's fate would make a partial send pass for a
 * complete synchronization.
 */
private sealed interface BatchOutcome {
    /** Confirmed by the server. [token] is the one that worked: subsequent batches reuse it. */
    data class Sent(val token: ModificationToken) : BatchOutcome

    /** The server rejects the session, renewed token included. */
    data object SessionLost : BatchOutcome

    /** Nothing was sent, nothing is lost: this batch will leave later. */
    data object Deferred : BatchOutcome
}
