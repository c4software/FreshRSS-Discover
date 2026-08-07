package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ReadingPositionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Position de lecture, dans le DataStore des scalaires.
 *
 * Un scalaire, donc DataStore et non Room : la règle de partage est celle
 * d'ARCHITECTURE.md §5.1, et une table d'une ligne serait une entité de plus à
 * migrer pour un seul entier.
 *
 * L'écriture est fréquente — l'écran signale chaque changement d'article en
 * tête — mais DataStore sérialise ses écritures et la valeur est minuscule.
 * L'appelant, lui, n'écrit que sur **changement**, jamais à chaque observation.
 */
@Singleton
internal class ReadingPositionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReadingPositionRepository {
    override suspend fun lastPosition(): ArticleId? =
        dataStore.data.first()[Key]?.let(::ArticleId)

    override suspend fun remember(articleId: ArticleId) {
        dataStore.edit { preferences -> preferences[Key] = articleId.value }
    }

    override suspend fun forget() {
        dataStore.edit { preferences -> preferences.remove(Key) }
    }

    private companion object {
        /**
         * Préfixe distinct de `session.` et des réglages : les trois cohabitent
         * dans le même fichier, et un effacement de session ne doit pas emporter
         * autre chose que ce qu'il vise.
         */
        val Key = longPreferencesKey("reading.last_article_id")
    }
}
