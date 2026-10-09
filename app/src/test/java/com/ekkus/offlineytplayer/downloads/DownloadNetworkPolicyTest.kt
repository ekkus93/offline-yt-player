package com.ekkus.offlineytplayer.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNetworkPolicyTest {
    @Test
    fun wifiOnlyAllowsOnlyUnmeteredConnectivity() {
        assertEquals(
            DownloadNetworkDecision.Allow,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.WifiOnly,
                DownloadConnectivity.Unmetered,
            ),
        )
        assertEquals(
            DownloadNetworkDecision.PauseForConnectivity,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.WifiOnly,
                DownloadConnectivity.Metered,
            ),
        )
        assertEquals(
            DownloadNetworkDecision.PauseForConnectivity,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.WifiOnly,
                DownloadConnectivity.None,
            ),
        )
    }

    @Test
    fun unmeteredNonWifiDoesNotBypassWifiOnlyButAnyNetworkStillAllowsIt() {
        assertEquals(
            DownloadNetworkDecision.PauseForConnectivity,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.WifiOnly,
                DownloadConnectivity.UnmeteredNonWifi,
            ),
        )
        assertEquals(
            DownloadNetworkDecision.Allow,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.AnyNetwork,
                DownloadConnectivity.UnmeteredNonWifi,
            ),
        )
    }

    @Test
    fun wifiOnlyRequiresWifiTransportRatherThanUnmeteredFlagAlone() {
        val wifi = DownloadConnectivityMapper.fromCapabilityFlags(
            hasInternet = true, isUnmetered = true, isWifi = true,
        )
        val unmeteredNonWifi = DownloadConnectivityMapper.fromCapabilityFlags(
            hasInternet = true, isUnmetered = true, isWifi = false,
        )
        val omittedTransport = DownloadConnectivityMapper.fromCapabilityFlags(
            hasInternet = true, isUnmetered = true,
        )
        val meteredWifi = DownloadConnectivityMapper.fromCapabilityFlags(
            hasInternet = true, isUnmetered = false, isWifi = true,
        )
        val disconnectedWifi = DownloadConnectivityMapper.fromCapabilityFlags(
            hasInternet = false, isUnmetered = true, isWifi = true,
        )
        assertEquals(DownloadConnectivity.Unmetered, wifi)
        assertEquals(DownloadConnectivity.UnmeteredNonWifi, unmeteredNonWifi)
        assertEquals(DownloadConnectivity.UnmeteredNonWifi, omittedTransport)
        assertEquals(DownloadConnectivity.Metered, meteredWifi)
        assertEquals(DownloadConnectivity.None, disconnectedWifi)
        assertEquals(
            DownloadNetworkDecision.PauseForConnectivity,
            DownloadNetworkPolicy.decision(DownloadNetworkPreference.WifiOnly, unmeteredNonWifi),
        )
        assertEquals(
            DownloadNetworkDecision.Allow,
            DownloadNetworkPolicy.decision(DownloadNetworkPreference.AnyNetwork, unmeteredNonWifi),
        )
    }

    @Test
    fun anyNetworkStillPausesWhenConnectivityIsLost() {
        assertEquals(
            DownloadNetworkDecision.Allow,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.AnyNetwork,
                DownloadConnectivity.Metered,
            ),
        )
        assertEquals(
            DownloadNetworkDecision.Allow,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.AnyNetwork,
                DownloadConnectivity.Unmetered,
            ),
        )
        assertEquals(
            DownloadNetworkDecision.PauseForConnectivity,
            DownloadNetworkPolicy.decision(
                DownloadNetworkPreference.AnyNetwork,
                DownloadConnectivity.None,
            ),
        )
    }

}
