package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un article dont le passage à « lu » reste à transmettre au serveur.
 *
 * Cette table existe parce que le marquage est **optimiste** (SPECS.md §4.5) :
 * l'état local change dès que l'article a été vu, l'envoi suit, et un échec
 * réseau ne doit pas se voir pendant la lecture. Il faut donc un endroit où
 * l'intention survit à l'échec — et à l'arrêt de l'application, d'où Room
 * plutôt qu'une liste en mémoire : un marquage non transmis est rejoué « y
 * compris après redémarrage ».
 *
 * La clé primaire est l'identifiant d'article lui-même : la file décrit un
 * ensemble d'articles à marquer, pas une suite d'événements. Deux passages sur
 * le même article n'ont rien à transmettre de plus qu'un seul, et une clé
 * autogénérée ferait grossir la file de doublons sans effet.
 */
@Entity(tableName = "pending_marks")
internal data class PendingMarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    /**
     * Date de mise en file.
     *
     * Sert l'ordre de rejeu : le plus ancien marquage part en premier, celui
     * qui a déjà le plus attendu. Sans elle, l'ordre serait celui, arbitraire,
     * que SQLite voudrait bien rendre.
     */
    @ColumnInfo(name = "queued_at_epoch_millis")
    val queuedAtEpochMillis: Long,
)
