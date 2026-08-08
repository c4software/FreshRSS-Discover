package fr.vbrosseau.freshrssdiscover.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.vbrosseau.freshrssdiscover.data.security.AndroidKeyStoreKeySource
import fr.vbrosseau.freshrssdiscover.data.security.KeystoreSecretCipher
import fr.vbrosseau.freshrssdiscover.data.security.SecretCipher
import fr.vbrosseau.freshrssdiscover.data.security.SecretKeySource

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityModule {
    @Binds
    abstract fun bindSecretCipher(implementation: KeystoreSecretCipher): SecretCipher

    @Binds
    abstract fun bindSecretKeySource(implementation: AndroidKeyStoreKeySource): SecretKeySource
}
