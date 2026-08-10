package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Accès à la file des marquages « lu » restant à transmettre. */
@Dao
internal interface PendingMarkDao {
    /**
     * Met des articles en file, sans jamais en dupliquer un déjà présent.
     *
     * `IGNORE` plutôt que `REPLACE` : la ligne existante conserve sa date de
     * mise en file d'origine. Avec `REPLACE`, un article revu à l'écran verrait
     * son horodatage repoussé et repasserait derrière les autres — un article
     * fréquemment revu pourrait ainsi ne jamais être transmis.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(marks: List<PendingMarkEntity>)

    /**
     * Les [limit] marquages en attente les plus anciens.
     *
     * L'ordre est celui de la mise en file : le rejeu commence par ce qui
     * attend depuis le plus longtemps. `article_id` départage les ex æquo pour
     * que deux appels successifs rendent la même chose — sans quoi une
     * transmission partielle pourrait retomber indéfiniment sur le même lot.
     */
    @Query(
        "SELECT article_id FROM pending_marks " +
            "ORDER BY queued_at_epoch_millis ASC, article_id ASC LIMIT :limit",
    )
    suspend fun pending(limit: Int): List<Long>

    /** Retire de la file les marquages dont la transmission est confirmée. */
    @Query("DELETE FROM pending_marks WHERE article_id IN (:articleIds)")
    suspend fun deleteByIds(articleIds: List<Long>)

    @Query("DELETE FROM pending_marks")
    suspend fun deleteAll()
}
