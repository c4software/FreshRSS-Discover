package fr.vbrosseau.freshrssdiscover.presentation.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Force la transmission des marquages quand l'application passe en arrière-plan.
 *
 * Le regroupement de `ReadTransmissionScheduler` retient les marquages cinq
 * secondes avant de les envoyer (SPECS.md §4.5). Rien n'est perdu si
 * l'utilisateur part pendant cette fenêtre — la file survit et le rejeu au
 * démarrage suivant la videra — mais, entre-temps, le serveur et les autres
 * appareils ignorent une lecture qui a bien eu lieu. Quitter l'application dans
 * les cinq secondes qui suivent une lecture est le geste le plus banal qui
 * soit ; cet observateur ferme la fenêtre au lieu de l'attendre.
 *
 * **`ON_STOP` et non `ON_PAUSE`.** `ON_PAUSE` se déclenche dès qu'une autre
 * fenêtre passe devant, y compris une boîte de dialogue système, une
 * notification développée ou un sélecteur de permission — l'application reste
 * visible, la lecture reprendra dans la seconde. Transmettre à chacun de ces
 * événements annulerait le regroupement précisément dans le cas qu'il sert à
 * couvrir. `ON_STOP` ne survient que lorsque l'application n'est plus visible :
 * plus rien ne sera lu, l'attente n'a plus d'objet.
 *
 * **L'application peut être tuée sans que l'événement arrive** — le système
 * n'en garantit aucun quand il récupère la mémoire d'un processus en
 * arrière-plan. Ce n'est pas un défaut à corriger ici : c'est exactement ce
 * pour quoi la file est persistante et n'est acquittée qu'après confirmation
 * du serveur. Cet observateur raccourcit le délai habituel, il ne remplace pas
 * le rejeu au démarrage.
 *
 * **La portée est celle de l'application, jamais celle d'un ViewModel.** La
 * transmission est déclenchée par la disparition de l'écran : lancée dans
 * `viewModelScope`, elle serait annulée par l'événement même qui l'a demandée.
 * D'où [ApplicationScope], dont le `SupervisorJob` vit aussi longtemps que le
 * processus.
 *
 * Un `DefaultLifecycleObserver` plutôt qu'un `@Composable` observant
 * `LocalLifecycleOwner` : ce n'est pas un écran qui a quelque chose à
 * transmettre, c'est l'application. Le brancher sur un Composable lierait la
 * synchronisation à la présence d'un écran donné dans l'arborescence — il se
 * tairait dès qu'on navigue ailleurs, et il faudrait le reposer sur chaque
 * écran ajouté.
 *
 * À enregistrer une fois, dans `MainActivity.onCreate()`, sur le cycle de vie
 * de l'unique `Activity` :
 *
 * ```kotlin
 * lifecycle.addObserver(readFlushOnBackgroundObserver)
 * ```
 *
 * Le cycle de vie du **processus** (`ProcessLifecycleOwner`) serait plus juste
 * — il ne produit qu'un `ON_STOP` par passage réel en arrière-plan, là où
 * celui d'une `Activity` en produit aussi un à chaque rotation et à chaque
 * ouverture d'un article dans l'onglet personnalisé. Il demande la dépendance
 * `androidx.lifecycle:lifecycle-process`, absente du dépôt. Le surcoût de s'en
 * passer est nul : transmettre sans rien en file ne touche pas au réseau, et
 * le marquage distant est idempotent. Cette classe n'observe rien elle-même,
 * donc le jour où la dépendance entre, seule la ligne d'enregistrement change.
 */
@Singleton
class ReadFlushOnBackgroundObserver @Inject constructor(
    private val readSyncRepository: ReadSyncRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : DefaultLifecycleObserver {

    /**
     * L'issue est ignorée volontairement : un échec de transmission laisse la
     * file intacte, et il n'y a plus d'écran pour en rendre compte.
     */
    override fun onStop(owner: LifecycleOwner) {
        applicationScope.launch { readSyncRepository.flush() }
    }
}
