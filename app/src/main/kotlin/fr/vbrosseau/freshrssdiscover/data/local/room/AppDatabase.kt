package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base locale de l'application.
 *
 * `exportSchema = true` : le schéma est versionné dans `app/schemas/`, ce qui
 * permet à Room de vérifier automatiquement les migrations et à une revue de
 * voir une évolution de base dans le diff (ARCHITECTURE.md §5.4).
 */
@Database(
    entities = [ArticleEntity::class, PendingMarkEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    abstract fun pendingMarkDao(): PendingMarkDao
}

/**
 * Ajoute la file des marquages en attente (`pending_marks`).
 *
 * Une vraie migration, et non `fallbackToDestructiveMigration` : une base en
 * version 1 peut déjà exister sur un appareil, et la détruire effacerait le
 * cache d'articles — donc, hors ligne, tout le contenu consultable (SPECS.md
 * §5.2). La table est simplement créée ; aucune donnée de la version 1 n'est
 * touchée, puisque rien de ce qui existait ne change de forme.
 *
 * L'instruction `CREATE TABLE` est recopiée telle quelle depuis le schéma
 * exporté `app/schemas/…/2.json` : c'est la seule façon d'obtenir une base
 * migrée strictement identique à une base créée de zéro. La moindre différence
 * — un `NOT NULL` oublié, un type écrit autrement — ferait échouer la
 * validation d'identité de Room au démarrage, et seulement chez les
 * utilisateurs qui migrent.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_marks` (" +
                "`article_id` INTEGER NOT NULL, " +
                "`queued_at_epoch_millis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`article_id`))",
        )
    }
}
