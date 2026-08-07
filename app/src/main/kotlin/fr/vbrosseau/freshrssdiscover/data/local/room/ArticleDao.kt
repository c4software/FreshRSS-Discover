package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Accès au cache des articles. */
@Dao
internal interface ArticleDao {
    /**
     * Les articles les plus récents, du plus récent au plus ancien.
     *
     * Trié sur la date de publication et non sur l'ordre d'insertion : le
     * serveur peut renvoyer une page dans un ordre quelconque, et l'affichage
     * doit rester chronologique inverse (SPECS.md §4.2, règle 2).
     */
    @Query(
        "SELECT * FROM articles ORDER BY published_at_epoch_seconds DESC, id DESC LIMIT :limit",
    )
    fun observeArticles(limit: Int): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Query("SELECT id FROM articles WHERE is_read = 1")
    suspend fun readArticleIds(): List<Long>

    /**
     * Enregistre une page en **conservant l'état lu déjà connu localement**.
     *
     * Un article lu sur l'appareil peut ne pas encore l'être côté serveur : le
     * marquage part hors ligne et n'est transmis qu'au retour du réseau
     * (SPECS.md §5.2). Une simple réécriture le ferait réapparaître comme non
     * lu au premier rafraîchissement — l'utilisateur verrait ressurgir ce
     * qu'il vient de lire. L'état local ne peut donc que progresser vers
     * « lu » ; le retour serveur, lui, peut le poser mais jamais le retirer.
     */
    @Transaction
    suspend fun upsertPreservingLocalReadState(articles: List<ArticleEntity>) {
        val locallyRead = readArticleIds().toSet()
        insertAll(articles.map { if (it.id in locallyRead) it.copy(isRead = true) else it })
    }

    /**
     * Bascule des articles à « lu » **localement**, sans rien attendre du
     * serveur.
     *
     * C'est le geste optimiste de SPECS.md §4.5 : l'état local change tout de
     * suite, la transmission suit. La mise à jour est volontairement partielle
     * — seul `is_read` bouge — parce que le reste de l'article n'a pas changé
     * et qu'une réécriture complète exigerait de le relire d'abord.
     *
     * Un identifiant absent du cache ne produit rien : l'article part quand
     * même vers le serveur, la file ne dépend pas de la présence en cache.
     */
    @Query("UPDATE articles SET is_read = 1 WHERE id IN (:articleIds)")
    suspend fun markAsRead(articleIds: List<Long>)

    /**
     * Supprime les articles lus entrés dans le cache avant [thresholdEpochMillis].
     *
     * La condition `is_read = 1` est la garantie demandée par SPECS.md §5.3 :
     * un article non lu n'est jamais purgé, quelle que soit son ancienneté.
     */
    @Query("DELETE FROM articles WHERE is_read = 1 AND cached_at_epoch_millis < :thresholdEpochMillis")
    suspend fun deleteReadCachedBefore(thresholdEpochMillis: Long): Int

    @Query("DELETE FROM articles")
    suspend fun deleteAll()
}
