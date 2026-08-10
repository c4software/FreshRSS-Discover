package fr.vbrosseau.freshrssdiscover.domain.read

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId

/**
 * Dépôt de synchronisation piloté, pour les tests.
 *
 * Reproduit la seule propriété qui compte pour l'appelant : [markAsRead] ne
 * peut pas échouer et n'attend rien. Ce que le faux enregistre, ce sont donc
 * les **appels**, pas des issues — un écran qui se contenterait de signaler
 * l'article vu doit être vérifiable sans jamais parler de réseau.
 *
 * [markedIds] est un cumul et non le dernier appel : le défilement en produit
 * un par lot d'articles vus, et n'observer que le dernier laisserait passer un
 * marquage écrasé par le suivant.
 */
class FakeReadSyncRepository : ReadSyncRepository {
    /** Tous les articles marqués depuis le début, dans l'ordre de marquage. */
    val markedIds: MutableList<ArticleId> = mutableListOf()

    /** Chaque appel à [markAsRead], tel qu'il a été reçu — les lots sont eux-mêmes observables. */
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
