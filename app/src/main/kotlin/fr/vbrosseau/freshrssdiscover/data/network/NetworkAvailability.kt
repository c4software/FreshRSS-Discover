package fr.vbrosseau.freshrssdiscover.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether the device has connectivity.
 *
 * Only used to distinguish "no network" from "server unreachable": the HTTP
 * stack reports both identically, while the fixes are unrelated
 * (SPECS.md §3.3).
 */
internal fun interface NetworkAvailability {
    fun isOnline(): Boolean
}

@Singleton
internal class AndroidNetworkAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkAvailability {
    /**
     * `NET_CAPABILITY_VALIDATED` in addition to `INTERNET`: a captive portal
     * advertises Internet access without providing it. Checking `INTERNET`
     * alone would diagnose "server unreachable" where the user actually needs
     * to accept the terms of a public Wi-Fi network.
     */
    override fun isOnline(): Boolean {
        val capabilities = context.getSystemService<ConnectivityManager>()
            ?.let { manager -> manager.getNetworkCapabilities(manager.activeNetwork) }

        return capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
