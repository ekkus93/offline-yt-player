package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadForegroundServicePolicyTest {
    @Test
    fun foregroundServicePolicyIsUserVisibleAndBounded() {
        assertEquals("offline_downloads", DownloadServicePolicy.ChannelId)
        assertTrue(DownloadServicePolicy.NotificationId > 0)
        assertEquals(2, DownloadServicePolicy.DefaultConcurrentDownloads)
        assertTrue(DownloadServicePolicy.ReconcilesDurableQueueOnStart)
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
    fun androidDoesNotOwnADuplicateConcurrencyMaximum() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        assertFalse(service.contains("const val MaxConcurrentDownloads"))
        assertTrue(service.contains("ffiMaxConcurrentDownloads"))
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

    @Test
    fun notificationControlsRouteThroughDurableGatewayDispatcher() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        assertTrue(service.contains("DownloadForegroundControlDispatcher.dispatch"))
        assertTrue(service.contains("GeneratedUniffiDownloadControlGateway.open"))
        assertTrue(service.contains("intent.getStringExtra(EXTRA_QUEUE_ITEM_ID)"))
        assertTrue(service.contains("serviceAction(ACTION_PAUSE, 1, queueItemId)"))
    }
}
