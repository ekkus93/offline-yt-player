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
        assertTrue(service.contains("override fun onTimeout(startId: Int, fgsType: Int)"))
        assertTrue(service.contains("DownloadForegroundTimeoutStore.persistTimeout"))
        assertTrue(service.indexOf("DownloadForegroundTimeoutStore.persistTimeout") < service.indexOf("stopForeground"))
        assertTrue(service.indexOf("DownloadForegroundTimeoutStore.persistTimeout") < service.indexOf("stopSelf(startId)"))
    }

    @Test
    fun manifestStillScopesDownloadServiceToDataSync() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".downloads.DownloadForegroundService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
    }
}
