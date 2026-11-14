package dev.tomasflopes.musicplayer.data.artistimage

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.tomasflopes.musicplayer.core.artistimage.NetworkKind

/** Unmetered covers Wi-Fi, Ethernet and a free hotspot; anything the carrier bills for is metered. */
fun ConnectivityManager.networkKind(): NetworkKind {
    val capabilities = getNetworkCapabilities(activeNetwork)
    val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    return when {
        !online -> NetworkKind.NONE
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true -> NetworkKind.UNMETERED
        else -> NetworkKind.METERED
    }
}
