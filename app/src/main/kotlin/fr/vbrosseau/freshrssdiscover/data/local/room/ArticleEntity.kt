package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un article tel qu'il est conservé sur l'appareil.
 *
 * La clé primaire est l'identifiant **décimal** de l'article, celui que le
 * domaine manipule (`ArticleId`). L'API expose aussi une forme hexadécimale ;
 * la laisser entrer en base rendrait possibles deux lignes pour un même
 * article, et le marquage comme lu échouerait alors en silence.
 *
 * Le titre du flux est dupliqué sur chaque ligne plutôt que rangé dans une
 * table de flux : le cache n'a qu'un seul lecteur, l'affichage, et une jointure
 * pour un unique libellé coûterait plus qu'elle n'économise (AGENTS.md §2 —
 * pas d'abstraction avant son deuxième usage).
 */
@Entity(tableName = "articles")
internal data class ArticleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "url")
    val url: String?,
    @ColumnInfo(name = "published_at_epoch_seconds")
    val publishedAtEpochSeconds: Long,
    @ColumnInfo(name = "summary")
    val summary: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String?,
    @ColumnInfo(name = "author")
    val author: String?,
    @ColumnInfo(name = "feed_id")
    val feedId: String,
    @ColumnInfo(name = "feed_title")
    val feedTitle: String,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean,
    /**
     * Date d'entrée — ou de rafraîchissement — de l'article dans le cache.
     *
     * Distincte de la date de publication : la purge (SPECS.md §5.3) borne
     * l'ancienneté **dans le cache**, pas celle de l'article. Purger sur la
     * publication ferait disparaître dans la seconde un vieil article qu'on
     * vient de lire, alors qu'il est encore visible à l'écran.
     */
    @ColumnInfo(name = "cached_at_epoch_millis")
    val cachedAtEpochMillis: Long,
)
