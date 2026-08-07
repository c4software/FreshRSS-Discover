package fr.vbrosseau.freshrssdiscover.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.local.SettingsStore
import fr.vbrosseau.freshrssdiscover.domain.settings.SettingsRepository

/**
 * Module propre aux réglages, séparé de `RepositoryModule`.
 *
 * Hilt n'impose pas de tout regrouper, et les réglages n'ont rien de commun
 * avec les dépôts réseau : un module dédié laisse le graphe lisible et évite
 * que deux travaux indépendants ne se croisent dans le même fichier.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
    @Binds
    abstract fun bindSettingsRepository(implementation: SettingsStore): SettingsRepository
}
