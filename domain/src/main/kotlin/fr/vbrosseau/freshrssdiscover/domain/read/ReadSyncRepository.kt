package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId

/**
 * Propagates to the server the articles [ReadDetector] declared read.
 *
 * Declared here, implemented in `:app`: the domain expresses what it needs
 * without knowing anything about HTTP or storage (ARCHITECTURE.md §2).
 *
 * Marking is optimistic (SPECS.md §4.5), which dictates the rest of this
 * interface. Local state flips immediately and transmission follows, so
 * recording the intent ([markAsRead]) and sending it ([flush]) are two
 * distinct operations. Merging them would make marking depend on the network:
 * an article would stay unread until the request succeeded, and a subway
 * tunnel would be enough to make just-read articles reappear in the feed.
 *
 * What is not transmitted is never lost. The queue survives restarts and is
 * only purged after server confirmation, article by article. One thing empties
 * it without confirmation: [clearPending], on sign-out.
 */
interface ReadSyncRepository {
    /**
     * Marks articles as read locally, enqueues the transmission, and schedules
     * its dispatch.
     *
     * Deliberately returns nothing: from the caller's perspective this
     * operation cannot fail. It does not wait for the network; it does not
     * even touch it. A result here would force the screen to handle a failure
     * it must not display (SPECS.md §4.5).
     *
     * The caller has nothing to send after this call. Temporal grouping
     * belongs to the implementation ([ReadTransmissionScheduler]): continuous
     * scrolling produces a batch every 200 ms, and sending them one by one
     * would be the per-article request that batched marking in SPECS.md §4.5
     * rules out. Chaining a [flush] here would defeat that grouping.
     *
     * Idempotent: the feed revisits the same articles while scrolling;
     * re-marking an already-read article does nothing.
     */
    suspend fun markAsRead(ids: Set<ArticleId>)

    /**
     * Transmits what is pending immediately, in batches, acknowledging only
     * after confirmation.
     *
     * The escape hatch from grouping: at startup, for the replay SPECS.md §4.5
     * promises for what could not leave before the app closed, and wherever
     * waiting no longer makes sense, e.g. when going to background. Ordinary
     * marking must not call it.
     *
     * With nothing queued, does not touch the network.
     *
     * Returns nothing, like [markAsRead] and for the same reason: no caller
     * has behavior to adapt to the outcome. A failure is a deferral (the queue
     * keeps everything, SPECS.md §4.5), and a rejected session is already
     * handled by the implementation, the root gate returning to the sign-in
     * screen by itself (SPECS.md §3.4).
     */
    suspend fun flush()

    /**
     * Drops what is pending, without transmitting.
     *
     * Reserved for sign-out (SPECS.md §3.5): these markings belong to the
     * account just left, and sending them under another session would mark
     * someone else's articles as read. This is the only way to empty the queue
     * without server confirmation; in particular, an expired session does not
     * empty it.
     */
    suspend fun clearPending()
}
