package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import java.io.Closeable

/**
 * Maps Android network capabilities into the app/core download-connectivity policy surface.
 *
 * The mapper is intentionally separate from [AndroidDownloadConnectivityObserver] so JVM tests can
 * qualify policy behavior while instrumentation tests exercise Android framework capability objects.
 */
internal object DownloadConnectivityMapper {
    fun fromCapabilities(capabilities: NetworkCapabilities?): DownloadConnectivity =
        fromCapabilityFlags(
            hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            isUnmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
            isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        )

    fun fromCapabilityFlags(
        hasInternet: Boolean,
        isUnmetered: Boolean,
        isWifi: Boolean = false,
    ): DownloadConnectivity {
        if (!hasInternet) return DownloadConnectivity.None
        if (!isUnmetered) return DownloadConnectivity.Metered
        // An unmetered Ethernet/cellular/VPN network is not Wi-Fi and must
        // not silently bypass the user's explicit Wi-Fi-only preference.
        return if (isWifi) DownloadConnectivity.Unmetered else DownloadConnectivity.UnmeteredNonWifi
    }
}


internal interface DownloadConnectivityPauseRegistry {
    fun markPausedByConnectivity(jobId: String)
    fun clearPausedByConnectivity(jobId: String)
    fun wasPausedByConnectivity(jobId: String): Boolean
}

internal class InMemoryDownloadConnectivityPauseRegistry : DownloadConnectivityPauseRegistry {
    private val jobIds = mutableSetOf<String>()

    @Synchronized
    override fun markPausedByConnectivity(jobId: String) {
        jobIds += jobId
    }

    @Synchronized
    override fun clearPausedByConnectivity(jobId: String) {
        jobIds -= jobId
    }

    @Synchronized
    override fun wasPausedByConnectivity(jobId: String): Boolean = jobId in jobIds
}

internal class SharedPreferencesDownloadConnectivityPauseRegistry(
    context: Context,
) : DownloadConnectivityPauseRegistry {
    private val preferences = context.applicationContext.getSharedPreferences(
        "download_connectivity_pauses",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    override fun markPausedByConnectivity(jobId: String) {
        mutate { it += jobId }
    }

    @Synchronized
    override fun clearPausedByConnectivity(jobId: String) {
        mutate { it -= jobId }
    }

    @Synchronized
    override fun wasPausedByConnectivity(jobId: String): Boolean =
        preferences.getStringSet(KEY_JOB_IDS, emptySet()).orEmpty().contains(jobId)

    private fun mutate(change: (MutableSet<String>) -> Unit) {
        val next = preferences.getStringSet(KEY_JOB_IDS, emptySet()).orEmpty().toMutableSet()
        change(next)
        preferences.edit().putStringSet(KEY_JOB_IDS, next).commit()
    }

    private companion object {
        const val KEY_JOB_IDS = "job_ids"
    }
}

/**
 * Small lifecycle-owned Android connectivity observer.
 *
 * Callers own [start]/[close] from an Activity, service, or future WorkManager host. Each callback
 * emits the current normalized connectivity state rather than raw platform objects, keeping the
 * durable queue policy testable and provider-neutral.
 */
internal class AndroidDownloadConnectivityObserver(
    context: Context,
    private val onConnectivityChanged: (DownloadConnectivity) -> Unit,
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java),
) : Closeable {
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            emitCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            onConnectivityChanged(DownloadConnectivityMapper.fromCapabilities(networkCapabilities))
        }

        override fun onLost(network: Network) {
            emitCurrentConnectivity()
        }

        override fun onUnavailable() {
            onConnectivityChanged(DownloadConnectivity.None)
        }
    }

    fun start() {
        if (registered) return
        emitCurrentConnectivity()
        connectivityManager.registerDefaultNetworkCallback(callback)
        registered = true
    }

    override fun close() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(callback)
        registered = false
    }

    private fun emitCurrentConnectivity() {
        val capabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        onConnectivityChanged(DownloadConnectivityMapper.fromCapabilities(capabilities))
    }
}

internal data class DownloadConnectivityCoordinatorReport(
    val decision: DownloadNetworkDecision,
    val pausedJobIds: List<String> = emptyList(),
    val resumedJobIds: List<String> = emptyList(),
    val errors: List<CoreGatewayError> = emptyList(),
)

/**
 * Applies connectivity policy to the durable queue through the same core gateways as UI and
 * notification controls.
 */
internal class DownloadConnectivityCoordinator(
    private val coreGateway: AppCoreGateway,
    private val controlGateway: AppDownloadControlGateway,
    private val networkPreference: () -> DownloadNetworkPreference,
    private val connectivityPauseRegistry: DownloadConnectivityPauseRegistry =
        InMemoryDownloadConnectivityPauseRegistry(),
) {
    fun onConnectivityChanged(connectivity: DownloadConnectivity): DownloadConnectivityCoordinatorReport {
        val decision = DownloadNetworkPolicy.decision(networkPreference(), connectivity)
        val queue = coreGateway.listDownloadQueue()
        queue.error?.let { error ->
            return DownloadConnectivityCoordinatorReport(decision = decision, errors = listOf(error))
        }

        val paused = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        val errors = mutableListOf<CoreGatewayError>()
        for (snapshot in queue.value.orEmpty()) {
            when {
                decision == DownloadNetworkDecision.PauseForConnectivity && snapshot.shouldPauseForConnectivity() -> {
                    val result = controlGateway.pause(snapshot.jobId)
                    result.error?.let(errors::add)
                    if (result.error == null && result.value == true) {
                        connectivityPauseRegistry.markPausedByConnectivity(snapshot.jobId)
                        paused += snapshot.jobId
                    }
                }
                decision == DownloadNetworkDecision.Allow &&
                    snapshot.state == CoreDownloadState.PAUSED &&
                    connectivityPauseRegistry.wasPausedByConnectivity(snapshot.jobId) -> {
                    val result = controlGateway.resume(snapshot.jobId)
                    result.error?.let(errors::add)
                    if (result.error == null && result.value == true) {
                        connectivityPauseRegistry.clearPausedByConnectivity(snapshot.jobId)
                        resumed += snapshot.jobId
                    }
                }
                snapshot.state == CoreDownloadState.FAILED ||
                    snapshot.state == CoreDownloadState.COMPLETED ||
                    snapshot.state == CoreDownloadState.CANCELED -> {
                    connectivityPauseRegistry.clearPausedByConnectivity(snapshot.jobId)
                }
            }
        }
        return DownloadConnectivityCoordinatorReport(
            decision = decision,
            pausedJobIds = paused,
            resumedJobIds = resumed,
            errors = errors,
        )
    }
}

private fun CoreDownloadSnapshot.shouldPauseForConnectivity(): Boolean = when (state) {
    CoreDownloadState.QUEUED,
    CoreDownloadState.RESOLVING,
    CoreDownloadState.DOWNLOADING,
    CoreDownloadState.RETRY_WAIT,
    CoreDownloadState.VERIFYING,
    -> true
    CoreDownloadState.PAUSED,
    CoreDownloadState.FAILED,
    CoreDownloadState.COMPLETED,
    CoreDownloadState.CANCELED,
    -> false
}
