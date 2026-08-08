package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshness
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fraîcheur du flux : la date persistée, l'acquittement en mémoire vive.
 *
 * **Deux supports, et ce n'est pas une inconséquence.** La date du dernier
 * contact serveur est un scalaire à conserver d'un lancement à l'autre — donc
 * DataStore, par ARCHITECTURE.md §5.1. L'acquittement, lui, n'a aucun sens à
 * survivre au processus : à la réouverture, ou bien une requête aboutit et la
 * date se remet à jour, ou bien elle échoue et c'est le bandeau hors ligne qui
 * parle. Le persister ajouterait une clé pour une situation qui ne se présente
 * pas.
 *
 * **`@Singleton` est ici une nécessité, pas une habitude.** C'est cet
 * acquittement en mémoire qui doit être partagé : les deux modes de
 * présentation (SPECS.md §4.8) ont chacun leur ViewModel, et basculer de l'un
 * à l'autre en détruit un pour en construire un autre. Un acquittement porté
 * par le ViewModel ferait reparaître la bandelette à chaque bascule.
 */
@Singleton
internal class FeedFreshnessStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : FeedFreshnessRepository {
    private val acknowledged = MutableStateFlow<Long?>(null)

    override fun observeFreshness(): Flow<FeedFreshness> =
        combine(
            dataStore.data.map { it[LastRefreshAtKey] },
            acknowledged,
        ) { lastRefreshAt, acknowledgedAt ->
            FeedFreshness(
                lastRefreshEpochMillis = lastRefreshAt,
                acknowledgedRefreshEpochMillis = acknowledgedAt,
            )
        }

    override suspend fun recordRefresh() {
        val now = clock.nowEpochMillis()
        dataStore.edit { preferences -> preferences[LastRefreshAtKey] = now }
    }

    /**
     * Acquitte l'horodatage **tel qu'il est écrit**, et non l'instant courant.
     *
     * C'est ce qui fait expirer l'acquittement tout seul : le prochain contact
     * serveur change la date, l'acquittement cesse de lui correspondre, et
     * l'avis pourra reparaître six heures plus tard (voir [FeedFreshness]).
     */
    override suspend fun acknowledgeStale() {
        acknowledged.value = dataStore.data.first()[LastRefreshAtKey]
    }

    private companion object {
        /**
         * Préfixe `feed.`, distinct de `session.`, `reading.` et des réglages :
         * les quatre cohabitent dans le même fichier, et un effacement ciblé ne
         * doit emporter que ce qu'il vise.
         */
        val LastRefreshAtKey = longPreferencesKey("feed.last_refresh_at")
    }
}
