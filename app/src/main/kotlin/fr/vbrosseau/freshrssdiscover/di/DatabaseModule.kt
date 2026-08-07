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
import javax.inject.Singleton

private const val DATABASE_FILE = "freshrss-discover.db"

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    /**
     * Aucune migration destructrice n'est déclarée : les schémas sont
     * versionnés (`app/schemas/`) et une évolution de base devra fournir sa
     * migration. Un `fallbackToDestructiveMigration` effacerait en silence les
     * marquages non encore transmis au serveur.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_FILE).build()

    @Provides
    fun provideArticleDao(database: AppDatabase): ArticleDao = database.articleDao()
}
