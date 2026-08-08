package fr.vbrosseau.freshrssdiscover.domain.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Nombre de minutes dans une journée, borne exclusive d'un moment de la journée. */
const val MINUTES_PER_DAY: Int = 24 * 60

/**
 * Le moment de la journée auquel le rappel part (SPECS.md §4.9).
 *
 * Exprimé en **minutes depuis minuit** plutôt qu'en `LocalTime` : c'est ce qui
 * se range dans un `DataStore` sans conversion, et la seconde n'a aucun sens
 * pour un rappel de lecture.
 *
 * L'heure retenue est celle de l'**ouverture de la veille**. Le rappel tombe
 * donc au moment où l'utilisateur a l'habitude d'ouvrir l'application, et non à
 * une heure choisie par le développeur — une notification à 9 h chez quelqu'un
 * qui lit le soir est une interruption, pas un rappel.
 */
@JvmInline
value class DailyMinute(val value: Int) {
    init {
        require(value in 0 until MINUTES_PER_DAY) { "moment de la journée hors bornes : $value" }
    }

    companion object {
        /** Le moment de la journée que porte cet instant, dans [zone]. */
        fun of(epochMillis: Long, zone: ZoneId): DailyMinute {
            val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
            return DailyMinute(time.hour * 60 + time.minute)
        }
    }
}

/**
 * Quand le prochain rappel doit partir.
 *
 * **Strictement dans le futur**, et c'est tout l'objet de cette fonction : à
 * l'instant où l'utilisateur ouvre l'application, l'heure d'ouverture du jour
 * est par construction déjà passée. Programmer « aujourd'hui à cette heure-là »
 * ferait partir la notification immédiatement, c'est-à-dire pendant qu'il lit.
 *
 * L'égalité stricte compte donc : à la seconde près, on vise le lendemain.
 *
 * @param zone la zone de l'utilisateur, transmise plutôt que lue : le domaine
 *   ne connaît ni horloge ni réglage système, et un test doit pouvoir se placer
 *   à Tokyo comme à Paris.
 */
fun nextReminderAt(at: DailyMinute, nowEpochMillis: Long, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
    val target = LocalTime.of(at.value / 60, at.value % 60)

    val today = now.toLocalDate().atTime(target).atZone(zone)
    val chosen = if (today.toInstant().toEpochMilli() > nowEpochMillis) {
        today
    } else {
        now.toLocalDate().plusDays(1).atTime(target).atZone(zone)
    }

    return chosen.toInstant().toEpochMilli()
}
