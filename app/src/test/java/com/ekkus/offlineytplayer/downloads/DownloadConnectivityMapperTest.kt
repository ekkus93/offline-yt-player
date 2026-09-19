package com.ekkus.offlineytplayer.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadConnectivityMapperTest {
    @Test
    fun noInternetMapsToNoUsableNetwork() {
        assertEquals(
            DownloadConnectivity.None,
            DownloadConnectivityMapper.fromCapabilityFlags(hasInternet = false, isUnmetered = false),
        )
    }

    @Test
    fun unmeteredFlagWithoutInternetStillMapsToNone() {
        assertEquals(
            DownloadConnectivity.None,
            DownloadConnectivityMapper.fromCapabilityFlags(hasInternet = false, isUnmetered = true),
        )
    }

    @Test
    fun meteredInternetMapsToMetered() {
        assertEquals(
            DownloadConnectivity.Metered,
            DownloadConnectivityMapper.fromCapabilityFlags(hasInternet = true, isUnmetered = false),
        )
    }

    @Test
    fun unmeteredInternetMapsToUnmetered() {
        assertEquals(
            DownloadConnectivity.Unmetered,
            DownloadConnectivityMapper.fromCapabilityFlags(hasInternet = true, isUnmetered = true),
        )
    }
}
