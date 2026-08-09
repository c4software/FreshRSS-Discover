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
     * Les articles les plus récents, **lus compris**, du plus récent au plus
     * ancien.
     *
     * Trié sur la date de publication et non sur l'ordre d'insertion : le
     * serveur peut renvoyer une page dans un ordre quelconque, et l'affichage
     * doit rester chronologique inverse (SPECS.md §4.2, règle 2).
     *
     * **Les lus ne sont pas écartés, et c'est le cœur de la stabilité du
     * lancement** (SPECS.md §5.1). Les écarter faisait changer l'ensemble entre
     * deux ouvertures — le marquage en consomme à chaque session — et le
     * mélange, qui choisit chaque position en regardant ses voisins, rendait
     * alors un ordre différent : le flux paraissait se remélanger tout seul.
     * Les articles lus restent donc affichés jusqu'au prochain rechargement
     * demandé, qui seul renouvelle la liste.
     *
     * Deux défauts sont morts avec ce choix. Le filtrage en Kotlin appliquait
     * la borne aux articles toutes catégories : un cache dont les deux cents
     * plus récents avaient été lus rendait une liste **vide** — l'écran le
     * croyait vide et lançait le chargement de secours à chaque ouverture.
     * Constaté sur appareil : 283 articles, 69 non lus, zéro affiché.
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
     * Les articles non lus du cache, du plus récent au plus ancien.
     *
     * Une **lecture ponctuelle** et non un `Flow` : l'appelant est le rappel de
     * lecture (SPECS.md §4.9), qui s'exécute une fois hors de toute interface.
     * Un flux l'obligerait à ouvrir puis refermer une souscription pour un seul
     * relevé, et à maintenir un observateur de base dans un travailleur qui a
     * vocation à se terminer.
     *
     * Le filtre est fait par SQLite plutôt qu'en Kotlin après coup : sans lui,
     * une pile entièrement lue rapporterait deux cents lignes pour n'en garder
     * aucune.
     */
    @Query(
        "SELECT * FROM articles WHERE is_read = 0 " +
            "ORDER BY published_at_epoch_seconds DESC, id DESC LIMIT :limit",
    )
    suspend fun unreadArticles(limit: Int): List<ArticleEntity>

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
     * Ne garde que [keptIds], et ce dont le marquage attend encore de partir.
     *
     * C'est le renouvellement du rechargement (SPECS.md §4.6, GOAL-027) : la
     * réponse du serveur devient le contenu du cache. Le critère est
     * l'**absence de la page rendue**, et non l'état lu local — lequel ignore
     * tout de ce qui a été lu ailleurs. Un article lu depuis l'interface web
     * cesse d'être renvoyé, et c'est le seul signe que l'application en reçoive
     * jamais : mesuré sur l'appareil de l'auteur, 31 articles « non lus »
     * localement alors que le serveur n'en avait plus aucun.
     *
     * La sous-requête sur `pending_marks` est la même garantie qu'en purge, et
     * elle pèse plus lourd ici : ces lignes portent une vérité que le serveur
     * **ne connaît pas encore**, et il ne les renvoie donc pas comme lues. Les
     * effacer perdrait la mémoire locale du « déjà lu », et l'article
     * reviendrait comme neuf.
     */
    @Query(
        "DELETE FROM articles WHERE id NOT IN (:keptIds) " +
            "AND id NOT IN (SELECT article_id FROM pending_marks)",
    )
    suspend fun deleteExcept(keptIds: List<Long>): Int

    /**
     * Le même renouvellement quand la page rendue est **vide**.
     *
     * Une requête distincte parce que `NOT IN ()` n'est pas du SQL valide :
     * Room n'insère aucun paramètre pour une liste vide, et la requête
     * échouerait à l'exécution — précisément dans le cas le plus courant, celui
     * du lecteur qui a tout lu.
     */
    @Query("DELETE FROM articles WHERE id NOT IN (SELECT article_id FROM pending_marks)")
    suspend fun deleteAllExceptPendingMarks(): Int

    /**
     * Supprime les articles lus **et synchronisés** entrés dans le cache avant
     * [thresholdEpochMillis].
     *
     * La condition `is_read = 1` est la première garantie de SPECS.md §5.4 : un
     * article non lu n'est jamais purgé, quelle que soit son ancienneté.
     *
     * La sous-requête sur `pending_marks` est la seconde, et elle corrige un
     * défaut réel. Sans elle, un appareil resté hors ligne plus longtemps que le
     * seuil voyait partir des articles lus dont le marquage n'était pas encore
     * transmis. La file, elle, survivait — le marquage aurait fini par arriver —
     * mais la **mémoire locale du « déjà lu »** disparaissait avec la ligne :
     * `upsertPreservingLocalReadState` la lit dans cette table et nulle part
     * ailleurs. Au rafraîchissement suivant, le serveur redécrivait l'article
     * comme non lu, plus rien ne le contredisait, et il **réapparaissait dans le
     * flux comme jamais lu**. Exactement la régression que tout le reste du
     * cache s'emploie à empêcher.
     */
    @Query(
        "DELETE FROM articles WHERE is_read = 1 " +
            "AND cached_at_epoch_millis < :thresholdEpochMillis " +
            "AND id NOT IN (SELECT article_id FROM pending_marks)",
    )
    suspend fun deleteReadCachedBefore(thresholdEpochMillis: Long): Int

    /** Nombre d'articles conservés, observable : l'écran de réglages l'affiche (SPECS.md §6). */
    @Query("SELECT COUNT(*) FROM articles")
    fun observeArticleCount(): Flow<Int>

    /**
     * Nombre d'articles que la purge emporterait **maintenant**.
     *
     * La condition est celle de [deleteReadCachedBefore] amputée de
     * l'ancienneté : c'est ce que le bouton de purge manuelle supprimerait. Elle
     * est écrite deux fois plutôt que partagée parce qu'une `@Query` est une
     * chaîne littérale — mais les deux doivent bouger ensemble, sinon l'écran
     * annoncerait un nombre que la purge ne tiendrait pas.
     *
     * Room suit les deux tables citées : retirer un marquage de la file fait
     * remonter ce compteur de lui-même.
     */
    @Query(
        "SELECT COUNT(*) FROM articles WHERE is_read = 1 " +
            "AND id NOT IN (SELECT article_id FROM pending_marks)",
    )
    fun observePurgeableCount(): Flow<Int>

    @Query("DELETE FROM articles")
    suspend fun deleteAll()
}
