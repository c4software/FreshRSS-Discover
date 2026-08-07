package fr.vbrosseau.freshrssdiscover.data.local.room

import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File des marquages « lu » restant à transmettre au serveur.
 *
 * Le marquage est **optimiste** (SPECS.md §4.5) : l'état local bascule dès que
 * l'article a été suffisamment vu, et l'envoi suit. Il faut donc quelque part
 * où poser l'intention en attendant que le réseau veuille bien la porter — un
 * échec de transmission ne doit pas se voir pendant la lecture. C'est cette
 * file, et elle est en base : un marquage non transmis est rejoué « à la
 * prochaine occasion, y compris après redémarrage de l'application ». Une
 * structure en mémoire perdrait au premier arrêt exactement ce que l'on
 * cherchait à sauver.
 *
 * Seul point de contact entre le domaine et Room pour cette table : les
 * entités ne franchissent pas cette frontière (ARCHITECTURE.md §2.1).
 *
 * L'horloge est injectée — la date de mise en file décide de l'ordre de rejeu,
 * un test doit pouvoir la piloter (AGENTS.md §2).
 */
@Singleton
internal class PendingMarkQueue @Inject constructor(
    private val dao: PendingMarkDao,
    private val clock: Clock,
) {
    /**
     * Met des articles en file.
     *
     * Idempotent : mettre deux fois le même article en file ne laisse qu'une
     * entrée. Le flux repasse sur les mêmes articles au gré du défilement, et
     * la file décrit ce qui reste à dire au serveur, pas ce qui s'est passé à
     * l'écran.
     */
    suspend fun enqueue(ids: List<ArticleId>) {
        val queuedAt = clock.nowEpochMillis()
        dao.insertAll(ids.map { PendingMarkEntity(articleId = it.value, queuedAtEpochMillis = queuedAt) })
    }

    /**
     * Les [limit] marquages les plus anciens restant à transmettre.
     *
     * Lire ne retire rien : voir [acknowledge]. La borne existe parce que
     * l'envoi se fait par lots (SPECS.md §4.5) — une file accumulée hors ligne
     * pendant des jours ne doit pas partir en une seule requête démesurée.
     */
    suspend fun pending(limit: Int): List<ArticleId> = dao.pending(limit).map(::ArticleId)

    /**
     * Retire de la file ce dont la transmission est **confirmée**.
     *
     * Cette opération est délibérément séparée de [pending] : retirer à la
     * lecture perdrait le marquage dès qu'une requête échoue, alors que la
     * raison d'être de cette file est précisément de ne rien perdre dans ce
     * cas. La ligne ne disparaît donc qu'après une réponse du serveur.
     */
    suspend fun acknowledge(ids: List<ArticleId>) {
        dao.deleteByIds(ids.map { it.value })
    }

    /** Nombre de marquages en attente, pour signaler une synchronisation en retard. */
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    /**
     * Vide la file. Appelé à la déconnexion (SPECS.md §3.5) : ces marquages
     * appartiennent au compte qui vient d'être quitté.
     */
    suspend fun clear() {
        dao.deleteAll()
    }
}
