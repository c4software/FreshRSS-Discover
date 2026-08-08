package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.settings.FeedPresentation
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conserve les seuils du marquage automatique entre deux lancements (SPECS.md §6).
 *
 * **Pourquoi ce stockage existe.** Avant lui, l'écran de réglages affichait des
 * constantes recopiées de `ReadDetector` dans `SettingsViewModel`. Deux
 * déclarations séparées, sans rien pour les relier : changer le défaut du
 * détecteur laissait l'écran annoncer l'ancienne valeur, et l'utilisateur
 * n'avait aucun moyen de s'apercevoir que le chiffre affiché n'était pas celui
 * appliqué. Ici, [ReadingSettings.Default] est la seule origine des valeurs
 * initiales, et c'est cette même donnée que l'écran affiche.
 *
 * Implémente directement [SettingsRepository] au lieu d'être enveloppé dans un
 * `DefaultSettingsRepository` : il n'y a ni source distante, ni secret à
 * chiffrer, ni conversion — une couche supplémentaire ne ferait que relayer
 * (AGENTS.md §2, ne pas anticiper). Le jour où un réglage viendra du serveur,
 * l'abstraction arrivera avec son deuxième cas d'usage.
 *
 * Il conserve aussi le **mode de présentation du flux** (SPECS.md §4.8) : Liste
 * ou Balayage. Même stockage, parce que c'est la même nature de donnée — une
 * préférence que l'utilisateur choisit et que l'application doit retrouver au
 * lancement suivant.
 *
 * Il partage le `DataStore<Preferences>` de l'application avec [SessionStore] :
 * ses clés sont préfixées `reading.` et `display.`, et une déconnexion — qui
 * n'efface que les clés `session.` — laisse donc les réglages en place. C'est
 * voulu : les seuils de lecture et le mode de présentation sont des préférences
 * d'usage, pas des données de compte.
 */
@Singleton
internal class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    /**
     * **`distinctUntilChanged` n'est pas une optimisation ici.** DataStore émet
     * à chaque écriture du **fichier**, pas de la clé : toucher à n'importe
     * quelle préférence — la date du dernier contact serveur, par exemple, qui
     * s'écrit à chaque page reçue — réémettrait ces réglages inchangés. Les
     * ViewModels du flux reconstruisent leur détecteur de lecture sur chaque
     * émission, et remettraient donc à zéro les chronomètres de visibilité en
     * cours (SPECS.md §4.5) au beau milieu d'une lecture.
     *
     * Une valeur illisible ou aberrante est **ramenée dans les bornes** plutôt
     * que de faire échouer la lecture.
     *
     * Le fichier de préférences peut avoir été écrit par une version antérieure
     * aux bornes actuelles, ou restauré depuis une sauvegarde. Lever ici
     * empêcherait l'application de démarrer pour un réglage secondaire ; la
     * valeur corrigée sera réécrite au prochain geste de l'utilisateur.
     */
    override fun observeReadingSettings(): Flow<ReadingSettings> =
        dataStore.data.map(::readSettings).distinctUntilChanged()

    override suspend fun setVisibleFraction(value: Float) {
        require(value in ReadingSettings.VisibleFractionRange) {
            "fraction visible hors bornes : $value"
        }
        dataStore.edit { it[Keys.VisibleFraction] = value }
    }

    override suspend fun setContinuousVisibilityMillis(value: Long) {
        require(value in ReadingSettings.ContinuousVisibilityRange) {
            "durée de visibilité continue hors bornes : $value"
        }
        dataStore.edit { it[Keys.ContinuousVisibilityMillis] = value }
    }

    /**
     * Le mode de présentation, relu à chaque changement du fichier.
     *
     * Même tolérance que les seuils, pour la même raison : un nom de mode
     * inconnu — version antérieure, sauvegarde restaurée, fichier abîmé —
     * retombe sur `Liste` plutôt que de faire échouer la lecture. Le détail est
     * dans `FeedPresentation.fromStoredName`.
     */
    override fun observeFeedPresentation(): Flow<FeedPresentation> =
        dataStore.data.map { FeedPresentation.fromStoredName(it[Keys.FeedPresentation]) }
            .distinctUntilChanged()

    override suspend fun setFeedPresentation(value: FeedPresentation) {
        dataStore.edit { it[Keys.FeedPresentation] = value.storedName }
    }

    /**
     * Le rappel est actif tant que l'utilisateur n'a rien dit : l'absence de
     * clé vaut `true`. Le contraire obligerait à aller l'allumer après avoir
     * accordé la permission, c'est-à-dire à dire oui deux fois.
     */
    override fun observeReminderEnabled(): Flow<Boolean> =
        dataStore.data.map { it[Keys.ReminderEnabled] ?: true }.distinctUntilChanged()

    override suspend fun setReminderEnabled(value: Boolean) {
        dataStore.edit { it[Keys.ReminderEnabled] = value }
    }

    private fun readSettings(preferences: Preferences): ReadingSettings = ReadingSettings.coerced(
        visibleFraction = preferences[Keys.VisibleFraction] ?: ReadingSettings.Default.visibleFraction,
        continuousVisibilityMillis = preferences[Keys.ContinuousVisibilityMillis]
            ?: ReadingSettings.Default.continuousVisibilityMillis,
    )

    /**
     * Les clés, préfixées par ce dont elles relèvent.
     *
     * Trois familles cohabitent dans le même fichier : `session.` (effacée à la
     * déconnexion), `reading.` (les seuils du marquage) et `display.` (ce que
     * l'utilisateur voit). Le mode de présentation n'est pas un `reading.` : il
     * ne dit rien de ce qui rend un article lu, il dit comment le flux se
     * parcourt. Les mélanger rendrait impossible d'effacer une famille sans
     * emporter les autres.
     */
    private object Keys {
        val VisibleFraction = floatPreferencesKey("reading.visible_fraction")
        val ContinuousVisibilityMillis = longPreferencesKey("reading.continuous_visibility_millis")

        /**
         * Une chaîne et non un entier : voir `FeedPresentation.storedName`, qui
         * explique pourquoi l'`ordinal` serait piégeux.
         */
        val FeedPresentation = stringPreferencesKey("display.feed_presentation")

        /**
         * Ni `reading.` ni `display.` : le rappel ne dit rien de ce qui rend un
         * article lu, et il agit précisément quand rien n'est affiché.
         */
        val ReminderEnabled = booleanPreferencesKey("reminder.enabled")
    }
}
