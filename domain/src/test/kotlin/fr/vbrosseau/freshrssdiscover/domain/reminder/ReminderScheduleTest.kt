package fr.vbrosseau.freshrssdiscover.domain.reminder

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

private fun at(
    zone: ZoneId,
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

/**
 * L'heure du rappel, éprouvée là où elle peut mentir.
 *
 * Les cas qui suivent ne sont pas des variantes d'un même calcul : ce sont les
 * quatre façons dont une date se comporte autrement qu'une soustraction —
 * l'instant déjà passé, le changement d'heure, le fuseau du lecteur, et une
 * horloge d'appareil manifestement fausse.
 */
class ReminderScheduleTest {
    @Test
    fun aTimeStillToComeTodayIsKeptForToday() {
        val now = at(PARIS, 2026, 3, 10, 8, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 10, 20, 0), next)
    }

    @Test
    fun aTimeAlreadyPassedGoesToTomorrow() {
        val now = at(PARIS, 2026, 3, 10, 21, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 11, 20, 0), next)
    }

    @Test
    fun theExactSameMinuteGoesToTomorrowRatherThanFiringNow() {
        // C'est **le** cas de cette fonction. L'heure enregistrée est celle de
        // l'ouverture ; à l'instant où on programme, elle vient de sonner.
        // Retenir « aujourd'hui » ferait partir la notification pendant que
        // l'utilisateur lit.
        val now = at(PARIS, 2026, 3, 10, 20, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 11, 20, 0), next)
    }

    @Test
    fun theResultIsAlwaysInTheFuture() {
        val now = at(PARIS, 2026, 3, 10, 12, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            assertTrue(nextReminderAt(DailyMinute(minute), now, PARIS) > now, "minute $minute")
        }
    }

    @Test
    fun theHourSurvivesTheSpringClockChange() {
        // Nuit du 28 au 29 mars 2026 à Paris : 2 h devient 3 h. Un calcul par
        // « maintenant + 24 h » décalerait le rappel d'une heure, et il
        // dériverait deux fois par an. Le rappel doit rester à 8 h.
        val now = at(PARIS, 2026, 3, 28, 9, 0)

        val next = nextReminderAt(DailyMinute(8 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 3, 29, 8, 0), next)
    }

    @Test
    fun theHourSurvivesTheAutumnClockChange() {
        // Nuit du 24 au 25 octobre 2026 : 3 h redevient 2 h, la journée dure
        // vingt-cinq heures.
        val now = at(PARIS, 2026, 10, 24, 9, 0)

        val next = nextReminderAt(DailyMinute(8 * 60), now, PARIS)

        assertEquals(at(PARIS, 2026, 10, 25, 8, 0), next)
    }

    @Test
    fun theReaderTimeZoneDecidesAndNotTheServerOne() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = at(tokyo, 2026, 3, 10, 21, 0)

        val next = nextReminderAt(DailyMinute(20 * 60), now, tokyo)

        assertEquals(at(tokyo, 2026, 3, 11, 20, 0), next)
    }

    @Test
    fun theMomentOfAnInstantIsReadInTheReaderZone() {
        // 23 h à Paris est le lendemain 7 h à Tokyo : le même instant n'est pas
        // le même moment de la journée, et c'est la zone du lecteur qui trie.
        val instant = at(PARIS, 2026, 3, 10, 23, 0)

        assertEquals(DailyMinute(23 * 60), DailyMinute.of(instant, PARIS))
        assertEquals(DailyMinute(7 * 60), DailyMinute.of(instant, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun aMomentOutsideTheDayIsRefusedAtConstruction() {
        // Une valeur hors bornes vient du code ou d'un disque corrompu, jamais
        // de l'utilisateur : la taire programmerait un rappel à une heure qui
        // n'existe pas.
        assertFailsWith<IllegalArgumentException> { DailyMinute(MINUTES_PER_DAY) }
        assertFailsWith<IllegalArgumentException> { DailyMinute(-1) }
    }
}
