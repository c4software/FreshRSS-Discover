package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.reminder.DailyMinute
import fr.vbrosseau.freshrssdiscover.domain.reminder.MINUTES_PER_DAY
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import fr.vbrosseau.freshrssdiscover.reminder.OpeningRecorder
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Le numéro du jour **local** que porte cet instant, compté depuis l'époque.
 *
 * Deux besoins s'en servent, et c'est pour cela qu'elle est nommée ici plutôt
 * que recopiée : savoir si le jour a changé depuis la dernière ouverture, et
 * donner au domaine l'index qui fait tourner la formulation du rappel
 * (`reminderPlanFor`). Les deux doivent compter les **mêmes** jours, sans quoi
 * la formulation changerait au milieu d'une journée de l'utilisateur.
 *
 * Local, et jamais UTC : « la première ouverture du jour » se compte dans le
 * jour de celui qui ouvre l'application, pas dans celui de Greenwich. À Paris,
 * une ouverture à 1 h du matin appartient déjà au jour suivant ; en UTC, elle
 * appartiendrait encore à la veille.
 */
internal fun localDayOf(
    epochMillis: Long,
    zone: ZoneId,
): Long = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

/**
 * Retient le moment de la **première** ouverture du jour (SPECS.md §4.9).
 *
 * C'est cette heure-là que le rappel du lendemain vise : le moment où
 * l'utilisateur tend la main vers l'application. Retenir la dernière ouverture
 * retiendrait au contraire un passage distrait — un coup d'œil de trente
 * secondes avant de dormir fixerait le rappel à minuit.
 *
 * D'où les **deux** clés. Le moment seul ne suffirait pas : sans savoir à quel
 * jour il se rapporte, il serait impossible de distinguer la première ouverture
 * du jour des suivantes, et chaque retour à l'application écraserait l'heure
 * retenue. Le jour stocké est ce qui rend [recordOpening] silencieux à partir
 * du deuxième appel.
 *
 * Partage le `DataStore<Preferences>` de l'application, avec le préfixe
 * `reminder.` — le même que celui du réglage porté par [SettingsStore]. Une
 * déconnexion n'efface que les clés `session.` et laisse donc l'heure en place :
 * l'habitude de lecture de l'utilisateur ne relève pas de son compte.
 *
 * **Sans portée `@Singleton`, délibérément.** L'objet ne retient rien — l'état
 * est dans le `DataStore`, qui est le singleton — et sa [zone] est lue à sa
 * construction : une instance unique figerait pour toute la vie du processus le
 * fuseau du démarrage, et l'utilisateur qui change de pays continuerait à voir
 * ses ouvertures datées à l'heure de l'ancien.
 */
internal class ReminderTimeStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val zone: ZoneId,
) : OpeningRecorder {

    /**
     * Le test du jour et l'écriture sont dans le même `edit` : DataStore y
     * sérialise les transactions, ce qu'un `first()` suivi d'un `edit` ne
     * garantirait pas. Deux ouvertures simultanées — un `Activity` recréé
     * pendant une rotation en est une — pourraient sinon passer toutes les deux
     * le test, et la seconde écraserait la première.
     */
    override suspend fun recordOpening() {
        val now = clock.nowEpochMillis()
        val day = localDayOf(now, zone)

        dataStore.edit { preferences ->
            if (preferences[Keys.OpeningDay] == day) return@edit

            preferences[Keys.OpeningDay] = day
            preferences[Keys.OpeningMinute] = DailyMinute.of(now, zone).value
        }
    }

    /**
     * Lecture ponctuelle et non `Flow` : l'appelant est le programmateur, qui
     * décide une fois et n'a rien à réobserver.
     *
     * Une valeur hors bornes est traitée comme une absence plutôt que relayée à
     * `DailyMinute`, dont le constructeur lèverait. Le fichier peut avoir été
     * écrit par une version antérieure ou restauré d'une sauvegarde, et faire
     * échouer la programmation du rappel pour cela empêcherait aussi la
     * prochaine ouverture d'en réécrire une bonne.
     */
    override suspend fun openingMinute(): DailyMinute? =
        dataStore.data.first()[Keys.OpeningMinute]
            ?.takeIf { it in 0 until MINUTES_PER_DAY }
            ?.let(::DailyMinute)

    private object Keys {
        val OpeningMinute = intPreferencesKey("reminder.opening_minute")

        /**
         * Le jour auquel [OpeningMinute] se rapporte. Sans lui, l'heure retenue
         * serait celle de la dernière ouverture et non de la première.
         */
        val OpeningDay = longPreferencesKey("reminder.opening_day")
    }
}
