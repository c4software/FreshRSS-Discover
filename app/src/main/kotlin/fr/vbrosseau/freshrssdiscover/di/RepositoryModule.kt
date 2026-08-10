package fr.vbrosseau.freshrssdiscover.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.local.FeedFreshnessStore
import fr.vbrosseau.freshrssdiscover.data.local.SettingsStore
import fr.vbrosseau.freshrssdiscover.data.local.room.CacheMaintenance
import fr.vbrosseau.freshrssdiscover.data.network.AndroidNetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultArticleRepository
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultAuthRepository
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.FeedFreshnessRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.CacheRepository
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository

/** Bindings from domain interfaces to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun bindAuthRepository(implementation: DefaultAuthRepository): AuthRepository

    @Binds
    abstract fun bindArticleRepository(implementation: DefaultArticleRepository): ArticleRepository

    @Binds
    abstract fun bindReadSyncRepository(implementation: DefaultReadSyncRepository): ReadSyncRepository

    @Binds
    abstract fun bindCacheRepository(implementation: CacheMaintenance): CacheRepository

    @Binds
    abstract fun bindFeedFreshnessRepository(
        implementation: FeedFreshnessStore,
    ): FeedFreshnessRepository

    @Binds
    abstract fun bindNetworkAvailability(implementation: AndroidNetworkAvailability): NetworkAvailability

    @Binds
    abstract fun bindSettingsRepository(implementation: SettingsStore): SettingsRepository
}
