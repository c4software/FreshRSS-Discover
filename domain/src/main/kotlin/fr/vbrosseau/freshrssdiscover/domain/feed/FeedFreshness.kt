package fr.vbrosseau.freshrssdiscover.domain.feed

import kotlinx.coroutines.flow.Flow

/**
 * Au-delà de ce délai sans réponse du serveur, le flux affiché est **ancien**
 * (SPECS.md §4.6).
 *
 * Six heures, et non une ou deux : rien ne se synchronise en arrière-plan
 * (SPECS.md §2), donc l'écran montre le cache jusqu'à ce que l'utilisateur
 * demande autre chose. Un seuil court transformerait l'invitation en réflexe
 * quotidien, et une invitation qu'on apprend à ignorer ne dit plus rien. Six
 * heures séparent nettement la session reprise dans l'heure — où le flux est
 * encore celui qu'on a laissé — de la réouverture du lendemain matin.
 */
const val STALE_FEED_THRESHOLD_MILLIS: Long = 6 * 60 * 60 * 1_000L

/**
 * Ce qu'on sait de la fraîcheur du flux affiché, et de ce que l'utilisateur en
 * a déjà entendu.
 *
 * Le calcul est ici, dans `:domain`, pour la même raison que `reminderPlanFor` :
 * l'instant courant est **transmis**, jamais lu. Une règle qui interroge sa
 * propre horloge ne s'éprouve qu'en attendant.
 *
 * @property lastRefreshEpochMillis dernière réponse valide du serveur. `null`
 *   quand il n'y en a jamais eu.
 * @property acknowledgedRefreshEpochMillis valeur de [lastRefreshEpochMillis]
 *   pour laquelle l'avis a été acquitté. Repérer l'acquittement par
 *   l'horodatage plutôt que par un simple drapeau suffit à le faire expirer
 *   tout seul : un rafraîchissement réussi change la valeur, l'acquittement
 *   cesse de correspondre, et l'avis pourra reparaître six heures plus tard.
 */
data class FeedFreshness(
    val lastRefreshEpochMillis: Long? = null,
    val acknowledgedRefreshEpochMillis: Long? = null,
) {
    /**
     * Le flux affiché date-t-il de plus de [STALE_FEED_THRESHOLD_MILLIS] ?
     *
     * **Sans point de référence, rien n'est ancien.** Au tout premier lancement
     * une requête est en vol : annoncer un flux périmé avant même la première
     * réponse accuserait le serveur d'un retard qui n'existe pas.
     *
     * **Une horloge qui recule ne rend rien ancien non plus.** L'écart devient
     * négatif, la comparaison est fausse, et c'est le bon résultat : ajouter
     * une règle « horodatage dans le futur = ancien » ferait surgir l'avis
     * juste après un rafraîchissement réussi, au moindre ajustement d'heure ou
     * à la restauration d'une sauvegarde. L'horodatage se corrige de lui-même
     * au prochain contact avec le serveur.
     */
    fun isStale(nowEpochMillis: Long): Boolean {
        val last = lastRefreshEpochMillis ?: return false
        return nowEpochMillis - last >= STALE_FEED_THRESHOLD_MILLIS
    }

    /**
     * Y a-t-il quelque chose à montrer à l'utilisateur ?
     *
     * C'est-à-dire : le flux est ancien, et il ne l'a pas déjà fait taire pour
     * cet horodatage-là.
     */
    fun showsStaleNotice(nowEpochMillis: Long): Boolean =
        isStale(nowEpochMillis) && acknowledgedRefreshEpochMillis != lastRefreshEpochMillis
}

/**
 * Retient quand le serveur a répondu pour la dernière fois, et ce que
 * l'utilisateur a acquitté.
 *
 * Déclaré ici, implémenté dans `:app` : le domaine dit ce dont il a besoin sans
 * rien connaître du disque (ARCHITECTURE.md §2).
 */
interface FeedFreshnessRepository {
    /** Émet à chaque changement, écriture de l'horodatage comme acquittement. */
    fun observeFreshness(): Flow<FeedFreshness>

    /**
     * Note que le serveur vient de répondre.
     *
     * Sans paramètre : l'implémentation lit l'horloge de l'appareil. C'est la
     * frontière — la règle de [FeedFreshness] reste sans horloge, l'écriture
     * en a forcément une.
     */
    suspend fun recordRefresh()

    /** L'utilisateur a fait taire l'avis pour l'horodatage courant. */
    suspend fun acknowledgeStale()
}
