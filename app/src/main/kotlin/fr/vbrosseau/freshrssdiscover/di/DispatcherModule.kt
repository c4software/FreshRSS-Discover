package fr.vbrosseau.freshrssdiscover.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Only place in the project referencing `kotlinx.coroutines.Dispatchers`.
 *
 * Everything else receives a qualified `CoroutineDispatcher`, which makes
 * tests deterministic: they substitute a test dispatcher and control time.
 *
 * No `Main`: ViewModels already live on the main thread via `viewModelScope`,
 * and nothing else needs it. The binding once existed "just in case" — its
 * only consumer was the test that verified it. No `@Singleton` either:
 * `Dispatchers.IO` and `Dispatchers.Default` are already platform singletons.
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
