package fr.vbrosseau.freshrssdiscover.presentation.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.vbrosseau.freshrssdiscover.di.ApplicationScope
import fr.vbrosseau.freshrssdiscover.reminder.OpeningRecorder
import fr.vbrosseau.freshrssdiscover.reminder.ReminderNotifier
import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ce que l'ouverture de l'application déclenche côté rappel (SPECS.md §4.9).
 *
 * Deux gestes, et ils vont ensemble parce qu'ils répondent au même événement :
 * l'utilisateur est là.
 *
 * **Le rappel affiché est retiré.** Il a rempli son office au moment où
 * l'utilisateur arrive ; le laisser dans le volet en ferait un reliquat que
 * l'on balaie sans y penser. Fait sans condition : l'appelant n'a pas à savoir
 * si un rappel était affiché, ni si l'utilisateur l'a déjà écarté.
 *
 * **L'heure d'ouverture est retenue, et le rappel du lendemain programmé.**
 * C'est là que se joue toute la règle de §4.9 : le rappel tombe à l'heure où
 * l'utilisateur ouvre habituellement l'application, et non à une heure choisie
 * par le développeur.
 *
 * **`ON_START` et non `ON_RESUME`.** `ON_RESUME` survient aussi au retour d'une
 * boîte de dialogue système ou de l'onglet personnalisé qui affiche un article
 * — l'application n'a jamais cessé d'être là, et compter ces retours comme des
 * ouvertures ferait dériver l'heure retenue vers le moment où l'on referme un
 * article. `ON_START` marque l'arrivée à l'écran.
 *
 * **La portée est celle de l'application.** L'enregistrement de l'heure et la
 * programmation du travail survivent ainsi à la destruction de l'écran, par
 * exemple lors d'une rotation immédiatement après le lancement.
 *
 * Le tri entre première et énième ouverture du jour n'est **pas** fait ici :
 * il appartient au magasin, seul à savoir quel jour la dernière heure
 * enregistrée concernait. Le décider ici obligerait cet observateur à tenir un
 * état, alors qu'il ne fait que relayer un événement.
 */
@Singleton
class ReminderOnForegroundObserver @Inject constructor(
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val openingRecorder: OpeningRecorder,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        notifier.dismiss()

        applicationScope.launch {
            openingRecorder.recordOpening()
            scheduler.scheduleNext()
        }
    }
}
