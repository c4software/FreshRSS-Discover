package fr.vbrosseau.freshrssdiscover.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.local.room.AppDatabase
import fr.vbrosseau.freshrssdiscover.data.local.room.ArticleDao
import fr.vbrosseau.freshrssdiscover.data.local.room.MIGRATION_1_2
import fr.vbrosseau.freshrssdiscover.data.local.room.PendingMarkDao
import javax.inject.Singleton

private const val DATABASE_FILE = "freshrss-discover.db"

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    /**
     * Aucune migration destructrice n'est déclarée : les schémas sont
     * versionnés (`app/schemas/`) et une évolution de base doit fournir sa
     * migration. Un `fallbackToDestructiveMigration` effacerait en silence les
     * marquages non encore transmis au serveur.
     *
     * **`addMigrations` n'est pas facultatif.** Sans lui, une installation déjà
     * en version 1 lève `IllegalStateException` au premier accès, et
     * l'application est inutilisable jusqu'à sa réinstallation. Les tests ne
     * l'attrapent pas : ils construisent la base en mémoire, donc toujours à la
     * version courante. C'est un défaut qui ne se voit que chez ceux qui
     * mettent à jour.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_FILE)
        .addMigrations(MIGRATION_1_2)
        .build()

    @Provides
    fun provideArticleDao(database: AppDatabase): ArticleDao = database.articleDao()

    @Provides
    fun providePendingMarkDao(database: AppDatabase): PendingMarkDao = database.pendingMarkDao()
}
