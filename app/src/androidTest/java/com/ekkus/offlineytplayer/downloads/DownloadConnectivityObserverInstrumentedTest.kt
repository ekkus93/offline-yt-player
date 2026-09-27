package com.ekkus.offlineytplayer.downloads

import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadConnectivityObserverInstrumentedTest {
    @Test
    fun mapsAndroidCapabilitiesToDownloadConnectivity() {
        assertEquals(
            DownloadConnectivity.None,
            DownloadConnectivityMapper.fromCapabilities(null),
        )

        val localOnly = NetworkCapabilities()
        assertEquals(
            DownloadConnectivity.None,
            DownloadConnectivityMapper.fromCapabilities(localOnly),
        )

        val metered = NetworkCapabilities()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        assertEquals(
            DownloadConnectivity.Metered,
            DownloadConnectivityMapper.fromCapabilities(metered),
        )

        val unmetered = NetworkCapabilities()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        assertEquals(
            DownloadConnectivity.Unmetered,
            DownloadConnectivityMapper.fromCapabilities(unmetered),
        )
    }
}
