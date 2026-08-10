package fr.vbrosseau.freshrssdiscover.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.domain.time.Clock
import javax.inject.Singleton

/** The only place in the project that calls `System.currentTimeMillis()`. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
