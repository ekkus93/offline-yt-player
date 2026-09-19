package com.ekkus.offlineytplayer.downloads

import android.net.NetworkCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class DownloadConnectivityMapperTest {
    @Test
    fun missingCapabilitiesMapToNoUsableNetwork() {
        assertEquals(DownloadConnectivity.None, DownloadConnectivityMapper.fromCapabilities(null))
    }

    @Test
    fun networkWithoutInternetCapabilityMapsToNone() {
        val capabilities = mock(NetworkCapabilities::class.java)
        `when`(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(false)

        assertEquals(DownloadConnectivity.None, DownloadConnectivityMapper.fromCapabilities(capabilities))
    }

    @Test
    fun meteredInternetMapsToMetered() {
        val capabilities = mock(NetworkCapabilities::class.java)
        `when`(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        `when`(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).thenReturn(false)

        assertEquals(DownloadConnectivity.Metered, DownloadConnectivityMapper.fromCapabilities(capabilities))
    }

    @Test
    fun unmeteredInternetMapsToUnmetered() {
        val capabilities = mock(NetworkCapabilities::class.java)
        `when`(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        `when`(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).thenReturn(true)

        assertEquals(DownloadConnectivity.Unmetered, DownloadConnectivityMapper.fromCapabilities(capabilities))
    }
}
