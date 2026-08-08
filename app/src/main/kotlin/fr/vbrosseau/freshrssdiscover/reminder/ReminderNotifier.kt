package fr.vbrosseau.freshrssdiscover.reminder

import fr.vbrosseau.freshrssdiscover.domain.reminder.ReminderPlan

/**
 * Ce qui affiche le rappel de lecture (SPECS.md §4.9).
 *
 * Une interface, alors qu'il n'y a qu'une implémentation : elle est la
 * frontière entre ce qui **décide** d'un rappel — éprouvable sans Android — et
 * ce qui le **montre**, qui demande un `NotificationManager`. Sans elle, le
 * travailleur serait intestable autrement qu'en instrumentation.
 */
interface ReminderNotifier {

    /**
     * Affiche le rappel décrit par [plan].
     *
     * Silencieux si le système refuse les notifications : l'utilisateur a déjà
     * dit non, et le travailleur n'a rien à en faire — échouer le ferait
     * réessayer pour un refus qui ne changera pas.
     */
    fun show(plan: ReminderPlan)

    /**
     * Retire le rappel affiché.
     *
     * Appelée à l'ouverture de l'application : le rappel a rempli son office au
     * moment où l'utilisateur arrive, et le laisser dans le volet en ferait un
     * reliquat que l'on balaie sans y penser — puis, le jour suivant, un
     * deuxième à côté du premier.
     *
     * Sans effet s'il n'y a rien d'affiché : l'appelant n'a pas à savoir si un
     * rappel est parti, ni si l'utilisateur l'a déjà écarté lui-même.
     */
    fun dismiss()
}
