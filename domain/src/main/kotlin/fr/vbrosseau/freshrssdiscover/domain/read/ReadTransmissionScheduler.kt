package fr.vbrosseau.freshrssdiscover.domain.read

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Délai de regroupement des marquages avant transmission (SPECS.md §8, question 4).
 *
 * Deux bornes, et la valeur est choisie entre elles :
 *
 * - **par le bas**, [ReadDetector] exige déjà 1 seconde de visibilité continue
 *   avant de déclarer un article lu : au rythme le plus rapide possible, il
 *   n'apparaît qu'un article lu par seconde. Une fenêtre plus courte que cette
 *   seconde se refermerait donc systématiquement sur **un seul** article, et
 *   produirait exactement la requête par article que SPECS.md §4.5 écarte ;
 * - **par le haut**, la fenêtre est le temps pendant lequel un marquage n'est
 *   connu que de l'appareil. Rien n'est perdu — la file survit à la fermeture
 *   et au redémarrage — mais tant qu'elle n'est pas transmise, la lecture
 *   n'est pas visible depuis le web ou un autre appareil. L'utilisateur qui
 *   lit puis quitte l'application est le cas courant, et il ne doit pas
 *   attendre le lancement suivant pour une lecture faite dix secondes plus tôt.
 *
 * 5 secondes valent cinq fois le seuil de détection : l'écran échantillonne la
 * visibilité toutes les 200 ms, donc une fenêtre regroupe jusqu'à 25 lots
 * détectés en **une** requête, pour au plus cinq articles — largement sous le
 * lot de 100 de la couche data. Et elle reste de l'ordre du geste : quitter
 * l'application dans les cinq secondes qui suivent une lecture est possible,
 * mais c'est déjà l'exception.
 */
private const val DEFAULT_GROUPING_DELAY_MILLIS = 5_000L

/**
 * Regroupe dans le temps les transmissions de marquages.
 *
 * Le marquage **local** ne passe pas par ici : il reste immédiat, c'est la
 * moitié optimiste de SPECS.md §4.5, et la retarder ferait réapparaître à
 * l'écran un article que l'utilisateur vient de lire. Seule la **transmission**
 * est différée.
 *
 * La fenêtre est **à échéance fixe** : un marquage survenant pendant l'attente
 * rejoint la fenêtre en cours sans la repousser. C'est le point à ne pas
 * inverser — un défilement continu produit un lot toutes les 200 ms, et une
 * fenêtre glissante ne se refermerait donc **jamais** tant que l'utilisateur
 * lit. La transmission n'aurait lieu qu'à l'arrêt du défilement, c'est-à-dire
 * souvent au moment même où l'application se ferme. L'échéance fixe borne au
 * contraire l'attente à [groupingDelayMillis], quoi que fasse l'utilisateur.
 *
 * Le temps vient de `delay` et non de `Clock` : c'est une attente, pas un
 * horodatage, et l'ordonnanceur virtuel de `kotlinx-coroutines-test` la rend
 * vérifiable sans attendre réellement.
 */
class ReadTransmissionScheduler(
    private val scope: CoroutineScope,
    private val groupingDelayMillis: Long = DEFAULT_GROUPING_DELAY_MILLIS,
    private val transmit: suspend () -> ReadSyncOutcome,
) {
    /** Protège [window] : un marquage et un envoi forcé n'arrivent pas du même fil. */
    private val windowMutex = Mutex()

    /**
     * Sérialise les transmissions.
     *
     * Deux envois simultanés liraient la même file avant que l'un des deux ne
     * l'acquitte, et enverraient donc deux fois les mêmes articles. Le marquage
     * distant est idempotent, mais la requête, elle, serait payée deux fois.
     */
    private val transmissionMutex = Mutex()

    /** Fenêtre en cours, ou `null` si aucune n'est ouverte. */
    private var window: Job? = null

    /**
     * Ouvre une fenêtre de regroupement si aucune ne l'est déjà.
     *
     * À appeler **après** avoir mis le marquage en file, jamais avant : c'est
     * cet ordre qui garantit qu'aucun marquage n'est perdu. Une fenêtre déjà
     * ouverte n'a pas encore commencé à transmettre — elle se referme avant —
     * donc elle emportera le marquage qui vient d'être mis en file ; et si elle
     * s'est refermée entre-temps, cet appel en ouvre une nouvelle.
     */
    suspend fun schedule() {
        windowMutex.withLock {
            if (window != null) return
            window =
                scope.launch {
                    delay(groupingDelayMillis)
                    /*
                     * La fenêtre se referme AVANT de transmettre, et c'est tout
                     * l'invariant : un marquage arrivant pendant la transmission
                     * trouve la place libre et ouvre sa propre fenêtre. Le
                     * refermer après laisserait ce marquage sans transmission
                     * programmée, alors que la transmission en cours a peut-être
                     * déjà lu la file.
                     */
                    releaseWindow(coroutineContext.job)
                    transmitExclusively()
                }
        }
    }

    /**
     * Transmet sans attendre la fin de la fenêtre.
     *
     * C'est ce que fait le rejeu au démarrage — où il n'y a rien à regrouper,
     * seulement à rattraper — et ce que fera un passage en arrière-plan, où
     * l'attente n'a plus d'objet puisque plus rien ne sera lu.
     */
    suspend fun transmitNow(): ReadSyncOutcome {
        closeWindow()
        return transmitExclusively()
    }

    /**
     * Renonce à la fenêtre en cours sans transmettre.
     *
     * Pour la déconnexion : la file étant abandonnée, la transmission
     * programmée n'a plus rien à dire au serveur.
     */
    suspend fun cancelScheduled() {
        closeWindow()
    }

    private suspend fun closeWindow() {
        windowMutex.withLock {
            window?.cancel()
            window = null
        }
    }

    /**
     * Libère la place, à l'usage de la fenêtre elle-même.
     *
     * Distincte de [closeWindow], qui annule : une fenêtre ne peut pas
     * s'annuler elle-même sans annuler la transmission qu'elle est en train de
     * déclencher. La comparaison d'identité protège du cas de course où un
     * envoi forcé a déjà refermé cette fenêtre et où une autre a été ouverte
     * depuis — l'oublier ferait perdre la fenêtre de quelqu'un d'autre.
     */
    private suspend fun releaseWindow(current: Job) {
        windowMutex.withLock { if (window === current) window = null }
    }

    private suspend fun transmitExclusively(): ReadSyncOutcome = transmissionMutex.withLock { transmit() }
}
