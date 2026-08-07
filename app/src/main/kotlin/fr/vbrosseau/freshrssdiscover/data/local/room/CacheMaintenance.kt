package fr.vbrosseau.freshrssdiscover.data.local.room

import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entretien du cache local : la purge, et la mesure de ce qu'il reste
 * (SPECS.md §5.4, §6).
 *
 * **Quand la purge automatique se déclenche : une fois par démarrage de
 * processus, en tâche de fond.** Les trois moments possibles n'ont pas le même
 * coût.
 *
 * - *Après chaque page* : le plus fréquent, et au pire moment. Une page arrive
 *   pendant que l'utilisateur fait défiler ; un `DELETE` qui parcourt toute la
 *   table et interroge au passage la file des marquages tomberait donc
 *   exactement sur l'instant où une saccade se voit. Vingt à trente fois par
 *   session pour un résultat qui ne change qu'au fil des jours.
 * - *Périodiquement* : il faudrait un ordonnanceur (`WorkManager`), donc une
 *   dépendance et un composant de plus, pour une opération qui n'a aucune raison
 *   de tourner quand l'application est fermée — un cache que personne ne lit
 *   n'encombre personne.
 * - *Au démarrage* : une fois par processus, sur [ApplicationScope] et sans que
 *   rien ne l'attende. Le premier affichage vient du cache (SPECS.md §5.1) et
 *   n'est pas suspendu à cette coroutine ; la purge ne peut donc pas retarder ce
 *   que l'utilisateur regarde. C'est ce qui est retenu.
 *
 * Le seul effet perceptible reste que des articles lus il y a plus d'une
 * semaine disparaissent du bas du flux entre deux lancements — c'est la purge
 * elle-même, pas son moment.
 */
@Singleton
internal class CacheMaintenance @Inject constructor(
    private val cache: ArticleCache,
    @ApplicationScope private val scope: CoroutineScope,
) : CacheRepository {

    override fun observeCacheStatus(): Flow<CacheStatus> = cache.observeCacheStatus()

    /**
     * Purge manuelle : tout ce qui est lu et synchronisé, sans attendre les
     * sept jours.
     *
     * Renoncer à l'ancienneté n'affaiblit aucune garantie : les articles non lus
     * et les marquages en attente restent hors d'atteinte.
     */
    override suspend fun purgeReadArticles(): Int = cache.purgeAllRead()

    /**
     * Lance la purge d'ancienneté sans l'attendre.
     *
     * À appeler une fois au démarrage. Elle ne renvoie rien : personne n'a de
     * décision à prendre à partir de son résultat, et le faire attendre
     * n'apporterait qu'un retard. L'échec est absorbé par le `SupervisorJob` de
     * la portée applicative — un cache non purgé est un désagrément, pas une
     * panne.
     */
    fun purgeExpiredInBackground() {
        scope.launch {
            val removed = cache.purgeReadOlderThan(CacheRepository.MaxAge)
            Timber.i("Purge du cache : %d article(s) lu(s) et synchronisé(s) supprimé(s)", removed)
        }
    }
}
