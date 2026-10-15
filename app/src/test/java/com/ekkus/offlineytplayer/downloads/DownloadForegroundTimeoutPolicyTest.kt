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
    }

    @Test
    fun manifestStillScopesDownloadServiceToDataSync() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".downloads.DownloadForegroundService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
    }
}
