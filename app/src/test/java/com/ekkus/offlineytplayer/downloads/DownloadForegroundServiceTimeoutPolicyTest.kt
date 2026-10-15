package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadForegroundServiceTimeoutPolicyTest {
    @Test
    fun inventoryNamesOnlyRetainedDownloadForegroundService() {
        assertEquals(
            "com.ekkus.offlineytplayer.downloads.DownloadForegroundService",
            DownloadForegroundServiceInventory.RetainedDataSyncService,
        )
        assertEquals(0, DownloadForegroundServiceInventory.RetainedMediaProcessingServices)
    }

    @Test
    fun manifestRetainsExactlyOneDownloadDataSyncForegroundService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("android:name=\".downloads.DownloadForegroundService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertTrue(manifest.contains("android:name=\".playback.PlaybackSessionService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaPlayback\""))
    }
}
