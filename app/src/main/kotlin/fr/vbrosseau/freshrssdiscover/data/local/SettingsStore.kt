package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
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
 * Il partage le `DataStore<Preferences>` de l'application avec [SessionStore] :
 * les clés sont préfixées `reading.`, et une déconnexion — qui n'efface que les
 * clés `session.` — laisse donc les réglages en place. C'est voulu : les seuils
 * de lecture sont une préférence d'usage, pas une donnée de compte.
 */
@Singleton
internal class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    /**
     * Une valeur illisible ou aberrante est **ramenée dans les bornes** plutôt
     * que de faire échouer la lecture.
     *
     * Le fichier de préférences peut avoir été écrit par une version antérieure
     * aux bornes actuelles, ou restauré depuis une sauvegarde. Lever ici
     * empêcherait l'application de démarrer pour un réglage secondaire ; la
     * valeur corrigée sera réécrite au prochain geste de l'utilisateur.
     */
    override fun observeReadingSettings(): Flow<ReadingSettings> = dataStore.data.map(::readSettings)

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

    private fun readSettings(preferences: Preferences): ReadingSettings = ReadingSettings.coerced(
        visibleFraction = preferences[Keys.VisibleFraction] ?: ReadingSettings.Default.visibleFraction,
        continuousVisibilityMillis = preferences[Keys.ContinuousVisibilityMillis]
            ?: ReadingSettings.Default.continuousVisibilityMillis,
    )

    private object Keys {
        val VisibleFraction = floatPreferencesKey("reading.visible_fraction")
        val ContinuousVisibilityMillis = longPreferencesKey("reading.continuous_visibility_millis")
    }
}
