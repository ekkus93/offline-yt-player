package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway

internal enum class DownloadConnectivityCommand {
    PauseActive,
    ResumeConnectivityPaused,
    Noop,
}

/**
 * Bridges Android network callbacks into durable download-control transitions.
 *
 * The coordinator deliberately tracks only jobs that this process paused for connectivity so a
 * restored network does not resume a user-paused item. Durable state remains owned by the core
 * gateway; this object only decides which gateway operation to request when Android reports a
 * connectivity change for the active foreground queue item.
 */
internal class DownloadConnectivityCoordinator {
    private val connectivityPausedJobIds = mutableSetOf<String>()

    fun command(
        preference: DownloadNetworkPreference,
        connectivity: DownloadConnectivity,
        queueItemId: String?,
    ): DownloadConnectivityCommand {
        val jobId = queueItemId?.trim()?.takeIf { it.isNotEmpty() } ?: return DownloadConnectivityCommand.Noop
        return when (DownloadNetworkPolicy.decision(preference, connectivity)) {
            DownloadNetworkDecision.PauseForConnectivity -> DownloadConnectivityCommand.PauseActive
            DownloadNetworkDecision.Allow -> if (jobId in connectivityPausedJobIds) {
                DownloadConnectivityCommand.ResumeConnectivityPaused
            } else {
                DownloadConnectivityCommand.Noop
            }
        }
    }

    fun dispatch(
        preference: DownloadNetworkPreference,
        connectivity: DownloadConnectivity,
        queueItemId: String?,
        gateway: AppDownloadControlGateway,
    ): Boolean {
        val jobId = queueItemId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return when (command(preference, connectivity, jobId)) {
            DownloadConnectivityCommand.PauseActive -> {
                val result = gateway.pause(jobId)
                if (result.error == null && result.value == true) {
                    connectivityPausedJobIds += jobId
                    true
                } else {
                    false
                }
            }
            DownloadConnectivityCommand.ResumeConnectivityPaused -> {
                val result = gateway.resume(jobId)
                if (result.error == null && result.value == true) {
                    connectivityPausedJobIds -= jobId
                    true
                } else {
                    false
                }
            }
            DownloadConnectivityCommand.Noop -> false
        }
    }
}

internal class DownloadConnectivityObserver(
    context: Context,
    private val preferenceProvider: () -> DownloadNetworkPreference,
    private val onConnectivityChanged: (DownloadNetworkPreference, DownloadConnectivity) -> Unit,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = emitCurrentConnectivity()
        override fun onLost(network: Network) = emitCurrentConnectivity()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            emit(networkCapabilities.toDownloadConnectivity(connectivityManager.isActiveNetworkMetered))
    }

    fun start() {
        if (started) return
        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
        emitCurrentConnectivity()
    }

    fun stop() {
        if (!started) return
        started = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }

    fun emitCurrentConnectivity() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        emit(capabilities.toDownloadConnectivity(connectivityManager.isActiveNetworkMetered))
    }

    private fun emit(connectivity: DownloadConnectivity) {
        onConnectivityChanged(preferenceProvider(), connectivity)
    }
}

internal fun NetworkCapabilities?.toDownloadConnectivity(isMetered: Boolean): DownloadConnectivity = when {
    this == null -> DownloadConnectivity.None
    !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> DownloadConnectivity.None
    !hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> DownloadConnectivity.None
    isMetered -> DownloadConnectivity.Metered
    else -> DownloadConnectivity.Unmetered
}
