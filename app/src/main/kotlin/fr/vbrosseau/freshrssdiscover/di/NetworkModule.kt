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

/** Seul endroit du projet où un moteur HTTP concret est choisi. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createFreshRssHttpClient(
        engine = OkHttp.create(),
        // La journalisation détaillée reste hors des constructions publiées :
        // même expurgée de l'en-tête d'autorisation, elle expose l'adresse du
        // serveur personnel de l'utilisateur.
        verboseLogging = BuildConfig.DEBUG,
    )
}
