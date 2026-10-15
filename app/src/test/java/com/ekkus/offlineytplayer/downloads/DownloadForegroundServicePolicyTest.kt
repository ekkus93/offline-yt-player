package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadForegroundServicePolicyTest {
    @Test
    fun unclaimedQueuedWorkerDoesNotImmediatelySpinAnotherWorker() {
        assertFalse(ForegroundWorkerRelaunchPolicy.shouldRelaunch(
            claimed = false,
            state = com.ekkus.offlineytplayer.coregateway.CoreDownloadState.QUEUED,
            decision = DownloadNetworkDecision.Allow,
        ))
        assertTrue(ForegroundWorkerRelaunchPolicy.shouldRelaunch(
            claimed = true,
            state = com.ekkus.offlineytplayer.coregateway.CoreDownloadState.QUEUED,
            decision = DownloadNetworkDecision.Allow,
        ))
        assertFalse(ForegroundWorkerRelaunchPolicy.shouldRelaunch(
            claimed = true,
            state = com.ekkus.offlineytplayer.coregateway.CoreDownloadState.COMPLETED,
            decision = DownloadNetworkDecision.Allow,
        ))
        assertFalse(ForegroundWorkerRelaunchPolicy.shouldRelaunch(
            claimed = true,
            state = com.ekkus.offlineytplayer.coregateway.CoreDownloadState.QUEUED,
            decision = DownloadNetworkDecision.PauseForConnectivity,
        ))
    }

    @Test
    fun foregroundServicePolicyIsUserVisibleAndBounded() {
        assertEquals("offline_downloads", DownloadServicePolicy.ChannelId)
        assertTrue(DownloadServicePolicy.NotificationId > 0)
        assertEquals(2, DownloadServicePolicy.DefaultConcurrentDownloads)
    }

    @Test
    fun concurrencyPreferenceCannotExceedCoreProvidedMaximum() {
        val coreMaximum = 4
        assertEquals(1, DownloadServicePolicy.concurrentDownloads(0, coreMaximum))
        assertEquals(2, DownloadServicePolicy.concurrentDownloads(2, coreMaximum))
        assertEquals(coreMaximum, DownloadServicePolicy.concurrentDownloads(coreMaximum, coreMaximum))
        assertEquals(coreMaximum, DownloadServicePolicy.concurrentDownloads(Int.MAX_VALUE, coreMaximum))
    }

    @Test
    fun manifestDeclaresNonExportedDataSyncForegroundService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("android:name=\".downloads.DownloadForegroundService\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
    }
}
