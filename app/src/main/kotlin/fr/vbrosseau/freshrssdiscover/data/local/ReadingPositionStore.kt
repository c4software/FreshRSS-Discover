package fr.vbrosseau.freshrssdiscover.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleId
import fr.vbrosseau.freshrssdiscover.domain.feed.ReadingPosition
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
    /**
     * La date est lue **avec** l'identifiant, ou rien n'est rendu.
     *
     * Une position sans date ne permettrait pas la reprise « au plus proche »,
     * et l'identifiant seul échoue presque toujours — l'article de tête est
     * celui que le marquage vient de rendre lu (voir [ReadingPosition]).
     */
    override suspend fun lastPosition(): ReadingPosition? {
        val preferences = dataStore.data.first()
        val id = preferences[ArticleIdKey]
        val publishedAt = preferences[PublishedAtKey]

        return if (id == null || publishedAt == null) {
            null
        } else {
            ReadingPosition(ArticleId(id), publishedAt)
        }
    }

    override suspend fun remember(position: ReadingPosition) {
        dataStore.edit { preferences ->
            preferences[ArticleIdKey] = position.articleId.value
            preferences[PublishedAtKey] = position.publishedAtEpochSeconds
        }
    }

    override suspend fun forget() {
        dataStore.edit { preferences ->
            preferences.remove(ArticleIdKey)
            preferences.remove(PublishedAtKey)
        }
    }

    private companion object {
        /**
         * Préfixe distinct de `session.` et des réglages : les trois cohabitent
         * dans le même fichier, et un effacement de session ne doit pas emporter
         * autre chose que ce qu'il vise.
         */
        val ArticleIdKey = longPreferencesKey("reading.last_article_id")
        val PublishedAtKey = longPreferencesKey("reading.last_article_published_at")
    }
}
