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
     * No destructive migration is declared: schemas are versioned
     * (`app/schemas/`) and a database change must provide its migration. A
     * `fallbackToDestructiveMigration` would silently erase marks not yet
     * transmitted to the server.
     *
     * `addMigrations` is not optional. Without it, an installation already at
     * version 1 throws `IllegalStateException` on first access, and the
     * application is unusable until reinstalled. Tests do not catch this:
     * they build the database in memory, hence always at the current version.
     * The defect only shows for users who upgrade.
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
