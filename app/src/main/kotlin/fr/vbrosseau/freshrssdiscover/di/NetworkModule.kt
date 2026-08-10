package fr.vbrosseau.freshrssdiscover.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.BuildConfig
import fr.vbrosseau.freshrssdiscover.data.api.createFreshRssHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/** The only place in the project where a concrete HTTP engine is chosen. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createFreshRssHttpClient(
        engine = OkHttp.create(),
        // Verbose logging stays out of release builds: even with the
        // authorization header redacted, it exposes the address of the user's
        // personal server.
        verboseLogging = BuildConfig.DEBUG,
    )
}
