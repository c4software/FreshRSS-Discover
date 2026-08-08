package fr.vbrosseau.freshrssdiscover.presentation.settings

import fr.vbrosseau.freshrssdiscover.reminder.ReminderScheduler

/**
 * Planificateur de rappel en mémoire, pour les tests.
 *
 * Il compte les appels **et** retient le dernier geste : le compte seul ne
 * distinguerait pas une programmation d'une annulation, or c'est précisément ce
 * que l'interrupteur des réglages doit faire correctement.
 */
class FakeReminderScheduler : ReminderScheduler {
    var scheduleCount: Int = 0
        private set

    var cancelCount: Int = 0
        private set

    override suspend fun scheduleNext() {
        scheduleCount++
    }

    override fun cancel() {
        cancelCount++
    }
}
