package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId

/**
 * Scripted sync repository for tests.
 *
 * Reproduces the one property that matters to the caller: [markAsRead] cannot
 * fail and expects nothing back. The fake therefore records calls, not
 * outcomes; a screen that merely reports an article as seen must be
 * verifiable without ever involving the network.
 *
 * [markedIds] accumulates rather than keeping the last call: scrolling
 * produces one call per batch of seen articles, and observing only the last
 * would miss a marking overwritten by the next.
 */
class FakeReadSyncRepository : ReadSyncRepository {
    /** All articles marked so far, in marking order. */
    val markedIds: MutableList<ArticleId> = mutableListOf()

    /** Each call to [markAsRead] as received, so the batches themselves are observable. */
    val markCalls: MutableList<Set<ArticleId>> = mutableListOf()

    var flushCallCount: Int = 0
        private set

    var clearPendingCallCount: Int = 0
        private set

    override suspend fun markAsRead(ids: Set<ArticleId>) {
        markCalls += ids
        markedIds += ids
    }

    override suspend fun flush() {
        flushCallCount++
    }

    override suspend fun clearPending() {
        clearPendingCallCount++
    }
}
