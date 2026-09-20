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
        assertTrue(DownloadForegroundServiceInventory.HandlesAndroid15Timeout)
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

    @Test
    fun timeoutHandlerPersistsBeforeStoppingForegroundService() {
        val serviceSource = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        val timeoutBody = serviceSource.substringAfter("override fun onTimeout(startId: Int, fgsType: Int)")
            .substringBefore("override fun onBind")

        val persistIndex = timeoutBody.indexOf("DownloadForegroundTimeoutStore.persistTimeout")
        val stopForegroundIndex = timeoutBody.indexOf("stopForeground(STOP_FOREGROUND_REMOVE)")
        val stopSelfIndex = timeoutBody.indexOf("stopSelf(startId)")

        assertTrue(persistIndex >= 0)
        assertTrue(stopForegroundIndex > persistIndex)
        assertTrue(stopSelfIndex > stopForegroundIndex)
        assertTrue(timeoutBody.contains("foregroundServiceType = fgsType"))
    }

    @Test
    fun timeoutStoreRecordsStartIdForegroundTypeAndCount() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        val storeBody = source.substringAfter("internal object DownloadForegroundTimeoutStore")
            .substringBefore("class DownloadForegroundService")

        assertTrue(storeBody.contains("LastStartIdKey"))
        assertTrue(storeBody.contains("LastForegroundServiceTypeKey"))
        assertTrue(storeBody.contains("TimeoutCountKey"))
        assertTrue(storeBody.contains(".putInt(LastStartIdKey, startId)"))
        assertTrue(storeBody.contains(".putInt(LastForegroundServiceTypeKey, foregroundServiceType)"))
        assertTrue(storeBody.contains("preferences.getInt(TimeoutCountKey, 0) + 1"))
    }
}
