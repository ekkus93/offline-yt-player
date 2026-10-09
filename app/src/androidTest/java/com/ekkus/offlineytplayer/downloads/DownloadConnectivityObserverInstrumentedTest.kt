package com.ekkus.offlineytplayer.downloads

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadConnectivityObserverInstrumentedTest {
    @Test
    fun unmeteredNonWifiAndroidCapabilitiesNeverSatisfyWifiOnly() {
        for (transport in listOf(
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_VPN,
        )) {
            val capabilities = NetworkRequest.Builder().apply {
                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                addTransportType(transport)
            }.build().networkCapabilities
            val mapped = DownloadConnectivityMapper.fromCapabilities(capabilities)
            assertEquals("transport=$transport", DownloadConnectivity.UnmeteredNonWifi, mapped)
            assertEquals(
                "transport=$transport",
                DownloadNetworkDecision.PauseForConnectivity,
                DownloadNetworkPolicy.decision(DownloadNetworkPreference.WifiOnly, mapped),
            )
        }

        val wifi = NetworkRequest.Builder().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        }.build().networkCapabilities
        assertEquals(DownloadConnectivity.Unmetered, DownloadConnectivityMapper.fromCapabilities(wifi))
        assertEquals(
            DownloadNetworkDecision.Allow,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.WifiOnly,
                DownloadConnectivityMapper.fromCapabilities(wifi),
            ),
        )
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
