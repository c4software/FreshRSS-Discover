package fr.vbrosseau.freshrssdiscover.presentation.feed

import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshness
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Périodicité du réexamen de l'ancienneté.
 *
 * **Il faut bien que quelque chose réveille la règle.** Le seuil de six heures
 * se franchit sans qu'aucun événement ne se produise : l'application peut
 * rester ouverte, écran éteint, sans qu'une seule page soit demandée. Sans ce
 * réveil, l'avis n'apparaîtrait qu'au prochain geste — c'est-à-dire jamais pour
 * qui rouvre l'application et attend d'y voir quelque chose.
 *
 * Cinq minutes de retard sont invisibles sur un seuil de six heures, et le coût
 * est dérisoire : l'écran échantillonne déjà la visibilité toutes les 200 ms.
 */
private const val STALE_CHECK_PERIOD_MILLIS = 5L * 60L * 1_000L

/**
 * Dit, à tout instant, si le flux affiché mérite qu'on invite à le rafraîchir
 * (SPECS.md §4.6).
 *
 * **Une classe partagée plutôt que deux fois le même code.** Les deux modes de
 * présentation (SPECS.md §4.8) ont chacun leur ViewModel, et la règle est la
 * même : deux copies divergeraient à la première correction. C'est aussi ce qui
 * rend l'acquittement cohérent d'un mode à l'autre — le dépôt est unique, et
 * cette classe ne fait que l'observer.
 *
 * [isStale] ne dit **pas** que la bandelette est à l'écran : c'est l'état de
 * chaque écran qui décide de la montrer, en y ajoutant ce que lui seul sait —
 * qu'il est hors ligne, qu'il rafraîchit déjà, qu'il n'a rien à afficher.
 */
class FeedStalenessWatcher(
    private val repository: FeedFreshnessRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _isStale = MutableStateFlow(false)
    val isStale: StateFlow<Boolean> = _isStale.asStateFlow()

    /**
     * Attente en cours de la prochaine échéance.
     *
     * Une seule à la fois : chaque changement de fraîcheur — contact serveur,
     * acquittement — rend la précédente caduque.
     */
    private var aging: Job? = null

    init {
        repository.observeFreshness()
            .onEach(::reconsider)
            .launchIn(scope)
    }

    /** L'utilisateur fait taire l'avis pour l'horodatage courant. */
    fun acknowledge() {
        _isStale.value = false
        scope.launch { repository.acknowledgeStale() }
    }

    private fun reconsider(freshness: FeedFreshness) {
        aging?.cancel()
        _isStale.value = freshness.showsStaleNotice(clock.nowEpochMillis())

        /*
         * Ne rien attendre quand le temps ne peut plus rien changer : sans
         * contact serveur enregistré rien ne vieillit, et un avis déjà acquitté
         * ne se rouvrira qu'au prochain contact — lequel produira une émission
         * et repassera par ici.
         */
        if (_isStale.value || !freshness.canGrowStale()) return

        aging = scope.launch {
            /*
             * Un sondage plutôt qu'une attente calculée : une horloge
             * d'appareil qui saute — fuseau, réglage manuel, synchronisation
             * réseau — rendrait le délai calculé faux, et l'avis attendrait
             * alors une échéance qui n'arrive jamais.
             */
            while (!freshness.showsStaleNotice(clock.nowEpochMillis())) {
                delay(STALE_CHECK_PERIOD_MILLIS)
            }
            _isStale.value = true
        }
    }
}

/** Reste-t-il quelque chose que le seul écoulement du temps puisse changer ? */
private fun FeedFreshness.canGrowStale(): Boolean =
    lastRefreshEpochMillis != null && acknowledgedRefreshEpochMillis != lastRefreshEpochMillis
