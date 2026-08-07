package fr.vbrosseau.freshrssdiscover.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base locale de l'application.
 *
 * `exportSchema = true` : le schéma est versionné dans `app/schemas/`, ce qui
 * permet à Room de vérifier automatiquement les migrations et à une revue de
 * voir une évolution de base dans le diff (ARCHITECTURE.md §5.4).
 */
@Database(
    entities = [ArticleEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
}
