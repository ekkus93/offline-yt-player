package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.downloads.DownloadRuntimePolicy
import com.ekkus.offlineytplayer.downloads.DownloadServicePolicy
import com.ekkus.offlineytplayer.downloads.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadServicePolicyTest {
    @Test
    fun serviceIsUserVisibleBoundedAndActionable() {
        assertTrue(DownloadServicePolicy.NotificationId > 0)
        assertTrue(DownloadServicePolicy.DefaultConcurrentDownloads in 1..4)
        assertEquals(4, DownloadServicePolicy.concurrentDownloads(Int.MAX_VALUE, 4))
        assertTrue(DownloadServicePolicy.SupportsPauseResumeCancel)
        assertTrue(DownloadServicePolicy.ReportsCompletionAndFailure)
        assertTrue(DownloadServicePolicy.ReconcilesDurableQueueOnStart)
    }

    @Test
    fun wifiPreferenceIsNeverSilentlyViolated() {
        val blocked = DownloadRuntimePolicy.evaluateNetwork(true, NetworkKind.Metered)
        assertFalse(blocked.mayStart)
        assertTrue(blocked.shouldRetryWhenConnected)
        assertEquals("Waiting for Wi-Fi", blocked.userReason)
        assertTrue(DownloadRuntimePolicy.evaluateNetwork(true, NetworkKind.Wifi).mayStart)
    }

    @Test
    fun connectivityLossPausesForRetry() {
        val offline = DownloadRuntimePolicy.evaluateNetwork(false, NetworkKind.Offline)
        assertFalse(offline.mayStart)
        assertTrue(offline.shouldRetryWhenConnected)
    }

    @Test
    fun recoveryReconcilesDurableJobsAndPartials() {
        assertEquals("resume", DownloadRuntimePolicy.recoveryAction(true, true))
        assertEquals("restart", DownloadRuntimePolicy.recoveryAction(true, false))
        assertEquals("cleanup", DownloadRuntimePolicy.recoveryAction(false, true))
        assertEquals("none", DownloadRuntimePolicy.recoveryAction(false, false))
    }
}
