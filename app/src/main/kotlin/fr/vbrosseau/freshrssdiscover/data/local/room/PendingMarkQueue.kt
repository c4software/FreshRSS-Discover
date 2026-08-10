package fr.vbrosseau.freshrssdiscover.data.local.room

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queue of read marks awaiting transmission to the server.
 *
 * Marking is optimistic (SPECS.md §4.5): the local state flips as soon as the
 * article has been sufficiently seen, and the send follows. The intent needs a
 * place to wait until the network can carry it — a transmission failure must
 * not be visible during reading. That place is this queue, and it is in the
 * database: an untransmitted mark is replayed at the next opportunity,
 * including after an application restart. An in-memory structure would lose at
 * the first shutdown exactly what it was meant to save.
 *
 * Sole point of contact between the domain and Room for this table: entities
 * do not cross this boundary (ARCHITECTURE.md §2.1).
 *
 * The clock is injected — the enqueue time decides the replay order, and a
 * test must be able to control it (AGENTS.md §2).
 */
@Singleton
internal class PendingMarkQueue @Inject constructor(
    private val dao: PendingMarkDao,
    private val clock: Clock,
) {
    /**
     * Enqueues articles.
     *
     * Idempotent: enqueuing the same article twice leaves one entry. The feed
     * revisits the same articles as the user scrolls, and the queue describes
     * what remains to tell the server, not what happened on screen.
     */
    suspend fun enqueue(ids: List<ArticleId>) {
        val queuedAt = clock.nowEpochMillis()
        dao.insertAll(ids.map { PendingMarkEntity(articleId = it.value, queuedAtEpochMillis = queuedAt) })
    }

    /**
     * The [limit] oldest marks still to transmit.
     *
     * Reading removes nothing: see [acknowledge]. The limit exists because
     * sending is batched (SPECS.md §4.5) — a queue accumulated over days
     * offline must not leave in one oversized request.
     */
    suspend fun pending(limit: Int): List<ArticleId> = dao.pending(limit).map(::ArticleId)

    /**
     * Removes from the queue what has been confirmed transmitted.
     *
     * Deliberately separate from [pending]: removing on read would lose the
     * mark as soon as a request fails, while this queue exists precisely to
     * lose nothing in that case. The row only disappears after a server
     * response.
     */
    suspend fun acknowledge(ids: List<ArticleId>) {
        dao.deleteByIds(ids.map { it.value })
    }

    /**
     * Empties the queue. Called on logout (SPECS.md §3.5): these marks belong
     * to the account that was just left.
     */
    suspend fun clear() {
        dao.deleteAll()
    }
}
