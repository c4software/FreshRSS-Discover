package fr.vbrosseau.freshrssdiscover.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constate si l'appareil a une connectivité.
 *
 * Sert uniquement à distinguer « pas de réseau » de « serveur injoignable » :
 * la pile HTTP rapporte les deux de façon identique, alors que les gestes de
 * correction n'ont rien à voir (SPECS.md §3.3).
 */
internal fun interface NetworkAvailability {
    fun isOnline(): Boolean
}

@Singleton
internal class AndroidNetworkAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkAvailability {
    /**
     * `NET_CAPABILITY_VALIDATED` en plus de `INTERNET` : un portail captif
     * annonce l'accès à Internet sans le fournir. S'en tenir à `INTERNET`
     * ferait diagnostiquer « serveur injoignable » là où l'utilisateur doit en
     * réalité accepter les conditions d'un réseau Wi-Fi public.
     */
    override fun isOnline(): Boolean {
        val capabilities = context.getSystemService<ConnectivityManager>()
            ?.let { manager -> manager.getNetworkCapabilities(manager.activeNetwork) }

        return capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
