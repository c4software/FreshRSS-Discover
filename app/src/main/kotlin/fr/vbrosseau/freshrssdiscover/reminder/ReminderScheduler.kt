package fr.vbrosseau.freshrssdiscover.reminder

/**
 * Ce qui programme le prochain rappel (SPECS.md §4.9).
 *
 * Appelé de deux endroits, et c'est voulu : à l'ouverture de l'application, qui
 * fixe l'heure du lendemain, et par le travailleur lui-même après avoir notifié.
 * Sans le second, la chaîne s'arrêterait au premier jour où l'application n'est
 * pas ouverte — c'est-à-dire précisément le jour où le rappel sert.
 */
interface ReminderScheduler {

    /**
     * Programme le rappel suivant, en remplaçant celui qui attendait.
     *
     * Idempotent : deux appels ne produisent qu'un rappel. C'est ce qui permet
     * de l'appeler à chaque ouverture sans tenir de compte.
     */
    suspend fun scheduleNext()

    /** Annule le rappel en attente — réglage désactivé, ou déconnexion. */
    fun cancel()
}
