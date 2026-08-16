package fr.vbrosseau.freshrssdiscover.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.recap.MlKitRecapGenerator
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapGenerator

/** Binding from the recap port to its on-device ML Kit implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecapModule {
    @Binds
    abstract fun bindRecapGenerator(implementation: MlKitRecapGenerator): RecapGenerator
}
