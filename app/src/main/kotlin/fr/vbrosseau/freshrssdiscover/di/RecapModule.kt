package fr.vbrosseau.freshrssdiscover.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.recap.MlKitRecapGenerator
import fr.vbrosseau.freshrssdiscover.domain.recap.RecapGenerator
import fr.vbrosseau.freshrssdiscover.presentation.recap.RecapLanguage
import java.util.Locale

/** Binding from the recap port to its on-device ML Kit implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecapModule {
    @Binds
    abstract fun bindRecapGenerator(implementation: MlKitRecapGenerator): RecapGenerator

    companion object {
        /**
         * Resolved at each call, not captured at construction: the user can
         * change the device language while the app is alive, and the digest
         * must follow. English display name — the form the prompt's
         * instructions use.
         */
        @Provides
        fun provideRecapLanguage(): RecapLanguage =
            RecapLanguage { Locale.getDefault().getDisplayLanguage(Locale.ENGLISH) }
    }
}
