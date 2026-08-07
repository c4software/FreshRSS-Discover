package fr.vbrosseau.freshrssdiscover.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.network.AndroidNetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.network.NetworkAvailability
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultArticleRepository
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultAuthRepository
import fr.vbrosseau.freshrssdiscover.data.repository.DefaultReadSyncRepository
import fr.vbrosseau.freshrssdiscover.domain.auth.AuthRepository
import fr.vbrosseau.freshrssdiscover.domain.feed.ArticleRepository
import fr.vbrosseau.freshrssdiscover.domain.read.ReadSyncRepository

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
    abstract fun bindNetworkAvailability(implementation: AndroidNetworkAvailability): NetworkAvailability
}
