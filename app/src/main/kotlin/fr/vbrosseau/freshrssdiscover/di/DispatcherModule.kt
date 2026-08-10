package fr.vbrosseau.freshrssdiscover.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Seul endroit du projet où `kotlinx.coroutines.Dispatchers` est référencé.
 *
 * Tout le reste reçoit un `CoroutineDispatcher` qualifié, ce qui rend les tests
 * déterministes : ils substituent un dispatcher de test et pilotent le temps.
 *
 * Pas de `Main` : les ViewModels vivent déjà sur le fil principal via
 * `viewModelScope`, et rien d'autre n'en a besoin. Le binding a existé « au
 * cas où » — son seul consommateur était le test qui le vérifiait lui-même.
 * Pas de `@Singleton` non plus : `Dispatchers.IO` et `Dispatchers.Default`
 * sont déjà des singletons de la plateforme.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
