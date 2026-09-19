package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Maps Android network capabilities to the download policy's portable connectivity states. */
internal object DownloadConnectivityMapper {
    fun fromCapabilities(capabilities: NetworkCapabilities?): DownloadConnectivity {
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return DownloadConnectivity.None
        }
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            DownloadConnectivity.Unmetered
        } else {
            DownloadConnectivity.Metered
        }
    }
}

/**
 * Production observer for connectivity changes. The callback receives only the bounded policy
 * state; Android Network/NetworkCapabilities objects do not escape this integration layer.
 */
internal class AndroidDownloadConnectivityObserver(
    context: Context,
    private val onConnectivityChanged: (DownloadConnectivity) -> Unit,
    private val connectivityManager: ConnectivityManager =
        requireNotNull(context.getSystemService(ConnectivityManager::class.java)),
) : AutoCloseable {
    private var started = false
    private var lastConnectivity: DownloadConnectivity? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publishCurrent()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            publish(DownloadConnectivityMapper.fromCapabilities(capabilities))
        }

        override fun onLost(network: Network) = publishCurrent()

        override fun onUnavailable() = publish(DownloadConnectivity.None)
    }

    fun start() {
        if (started) return
        started = true
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        publishCurrent()
    }

    override fun close() {
        if (!started) return
        started = false
        connectivityManager.unregisterNetworkCallback(callback)
    }

    private fun publishCurrent() {
        val network = connectivityManager.activeNetwork
        publish(DownloadConnectivityMapper.fromCapabilities(network?.let(connectivityManager::getNetworkCapabilities)))
    }

    private fun publish(connectivity: DownloadConnectivity) {
        if (lastConnectivity == connectivity) return
        lastConnectivity = connectivity
        onConnectivityChanged(connectivity)
    }
}
