package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadForegroundTimeoutPolicyTest {
    @Test
    fun inventoryIdentifiesOnlyRetainedDataSyncForegroundService() {
        assertEquals(
            "com.ekkus.offlineytplayer.downloads.DownloadForegroundService",
            DownloadForegroundServiceInventory.RetainedDataSyncService,
        )
        assertEquals(0, DownloadForegroundServiceInventory.RetainedMediaProcessingServices)
        assertTrue(DownloadForegroundServiceInventory.HandlesAndroid15Timeout)
    }

    @Test
    fun foregroundServiceImplementsTimeoutBeforeStopping() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        val timeoutBlock = service.substringAfter("override fun onTimeout(startId: Int, fgsType: Int)")
            .substringBefore("override fun onBind")
        assertTrue(timeoutBlock.contains("DownloadForegroundTimeoutStore.persistTimeout"))
        assertTrue(timeoutBlock.contains("stopForeground(STOP_FOREGROUND_REMOVE)"))
        assertTrue(timeoutBlock.contains("stopSelf(startId)"))
        assertTrue(timeoutBlock.indexOf("DownloadForegroundTimeoutStore.persistTimeout") < timeoutBlock.indexOf("stopForeground"))
        assertTrue(timeoutBlock.indexOf("DownloadForegroundTimeoutStore.persistTimeout") < timeoutBlock.indexOf("stopSelf(startId)"))
    }

    @Test
    fun manifestStillScopesDownloadServiceToDataSync() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".downloads.DownloadForegroundService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
    }
}
