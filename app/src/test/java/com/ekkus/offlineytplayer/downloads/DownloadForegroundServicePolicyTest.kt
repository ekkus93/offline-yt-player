package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadForegroundServicePolicyTest {
    @Test
    fun foregroundServicePolicyIsUserVisibleAndBounded() {
        assertEquals("offline_downloads", DownloadServicePolicy.ChannelId)
        assertTrue(DownloadServicePolicy.NotificationId > 0)
        assertTrue(DownloadServicePolicy.MaxConcurrentDownloads in 1..4)
        assertTrue(DownloadServicePolicy.ReconcilesDurableQueueOnStart)
    }

    @Test
    fun manifestDeclaresNonExportedDataSyncForegroundService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("android:name=\".downloads.DownloadForegroundService\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
    }

    @Test
    fun serviceStartsForegroundAndHasExplicitStopLifecycle() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        assertTrue(service.contains("startForeground(DownloadServicePolicy.NotificationId"))
        assertTrue(service.contains("stopForeground(STOP_FOREGROUND_REMOVE)"))
        assertTrue(service.contains("stopSelf()"))
        assertTrue(service.contains("return START_STICKY"))
    }
}
