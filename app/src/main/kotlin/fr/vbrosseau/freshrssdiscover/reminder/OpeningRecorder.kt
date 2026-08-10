package fr.vbrosseau.freshrssdiscover.reminder

import fr.vbrosseau.freshrssdiscover.domain.reminder.DailyMinute

/**
 * Ce qui retient le moment d'une ouverture.
 *
 * Une interface plutôt qu'un appel direct au magasin : c'est elle qui permet à
 * `ReadingReminderWorkerTest` d'éprouver le travailleur avec un faux, sans
 * `DataStore`, sans horloge ni fuseau à lui transmettre — le magasin possède
 * déjà les deux.
 */
interface OpeningRecorder {

    /**
     * Retient l'heure qu'il est, si c'est la **première** ouverture du jour.
     *
     * Le silence des ouvertures suivantes est la règle et non une optimisation :
     * l'heure recherchée est celle où l'utilisateur tend la main vers
     * l'application, pas celle d'un passage distrait en fin de journée.
     */
    suspend fun recordOpening()

    /** Le moment retenu, ou `null` si l'application n'a jamais été ouverte. */
    suspend fun openingMinute(): DailyMinute?
}
