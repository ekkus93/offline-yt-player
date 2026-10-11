package com.ekkus.offlineytplayer.downloads

import android.app.job.JobInfo
import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadConnectivityObserverInstrumentedTest {
    @Test
    fun unmeteredNonWifiTransportFlagsNeverSatisfyWifiOnly() {
        // Android does not expose public NetworkCapabilities mutators on API 29.
        // Exercise the same production mapping flags on the packaged device; the
        // framework-object test below separately verifies actual capability extraction.
        for (transport in listOf(
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_VPN,
        )) {
            val mapped = DownloadConnectivityMapper.fromCapabilityFlags(
                hasInternet = true,
                isUnmetered = true,
                isWifi = transport == NetworkCapabilities.TRANSPORT_WIFI,
            )
            assertEquals("transport=$transport", DownloadConnectivity.UnmeteredNonWifi, mapped)
            assertEquals(
                "transport=$transport",
                DownloadNetworkDecision.PauseForConnectivity,
                DownloadNetworkPolicy.decision(DownloadNetworkPreference.WifiOnly, mapped),
            )
        }

        val wifi = DownloadConnectivityMapper.fromCapabilityFlags(
            hasInternet = true,
            isUnmetered = true,
            isWifi = true,
        )
        assertEquals(DownloadConnectivity.Unmetered, wifi)
        assertEquals(
            DownloadNetworkDecision.Allow,
            DownloadNetworkPolicy.decision(DownloadNetworkPreference.WifiOnly, wifi),
        )
    }

    @Test
    fun uidtWifiOnlyJobRequiresActualUnmeteredWifiTransport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = ComponentName(context, DownloadUserInitiatedJobService::class.java)
        val wifiJob = DownloadExecutionSchedulerPolicy.applyNetworkConstraint(
            JobInfo.Builder(DownloadUserInitiatedJobService.JobIdBase, service),
            DownloadNetworkPreference.WifiOnly,
        ).build()
        val network = wifiJob.requiredNetwork
        assertTrue("Wi-Fi-only job must require Wi-Fi transport", network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
        assertTrue("Wi-Fi-only job must reject metered Wi-Fi", network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true)
        assertTrue("Wi-Fi-only job must require Internet", network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        assertTrue("Ethernet alone must not satisfy Wi-Fi-only", network?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) != true)

        val anyJob = DownloadExecutionSchedulerPolicy.applyNetworkConstraint(
            JobInfo.Builder(DownloadUserInitiatedJobService.JobIdBase + 1, service),
            DownloadNetworkPreference.AnyNetwork,
        ).build()
        assertEquals(JobInfo.NETWORK_TYPE_ANY, anyJob.networkType)
    }

    @Test
    fun mapsAndroidCapabilitiesToDownloadConnectivity() {
        assertEquals(
            DownloadConnectivity.None,
            DownloadConnectivityMapper.fromCapabilities(null),
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        val expected = DownloadConnectivityMapper.fromCapabilityFlags(
            hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            isUnmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
            isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        )

        assertEquals(expected, DownloadConnectivityMapper.fromCapabilities(capabilities))
    }
}
