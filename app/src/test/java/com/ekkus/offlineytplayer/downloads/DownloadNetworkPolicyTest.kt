package com.ekkus.offlineytplayer.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun networkPreferenceCannotBeSilentlyViolated() {
        assertTrue(DownloadNetworkPolicy.SupportsWifiOnly)
        assertTrue(DownloadNetworkPolicy.PausesOnConnectivityLoss)
        assertFalse(DownloadNetworkPolicy.SilentPreferenceViolationAllowed)
        assertTrue(DownloadServicePolicy.HonorsNetworkPreference)
    }
}
