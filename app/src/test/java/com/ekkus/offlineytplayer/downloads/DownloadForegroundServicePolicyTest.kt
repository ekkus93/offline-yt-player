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
        assertEquals(2u, DownloadServicePolicy.concurrentDownloads())
        assertTrue(DownloadServicePolicy.maxConcurrentDownloads() >= DownloadServicePolicy.concurrentDownloads())
        assertTrue(DownloadServicePolicy.ReconcilesDurableQueueOnStart)
    }

    @Test
    fun concurrencyPreferenceIsBoundedByPortableCorePolicy() {
        val maximum = DownloadServicePolicy.maxConcurrentDownloads()
        assertTrue(maximum >= 1u)
        assertEquals(1u, DownloadServicePolicy.concurrentDownloads(0u))
        assertEquals(maximum, DownloadServicePolicy.concurrentDownloads(maximum))
        assertEquals(maximum, DownloadServicePolicy.concurrentDownloads(UInt.MAX_VALUE))
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
