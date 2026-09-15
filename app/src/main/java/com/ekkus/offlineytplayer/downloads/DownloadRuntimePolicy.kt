package com.ekkus.offlineytplayer.downloads

internal enum class NetworkKind { Wifi, Metered, Offline }

internal data class DownloadRuntimeDecision(
    val mayStart: Boolean,
    val shouldRetryWhenConnected: Boolean,
    val userReason: String?,
)

internal object DownloadRuntimePolicy {
    fun evaluateNetwork(wifiOnly: Boolean, network: NetworkKind): DownloadRuntimeDecision = when {
        network == NetworkKind.Offline -> DownloadRuntimeDecision(false, true, "Waiting for network")
        wifiOnly && network != NetworkKind.Wifi -> DownloadRuntimeDecision(false, true, "Waiting for Wi-Fi")
        else -> DownloadRuntimeDecision(true, false, null)
    }

    fun recoveryAction(hasDurableJob: Boolean, partialExists: Boolean): String = when {
        hasDurableJob && partialExists -> "resume"
        hasDurableJob -> "restart"
        partialExists -> "cleanup"
        else -> "none"
    }
}
