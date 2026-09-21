package com.ekkus.offlineytplayer.settings

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StorageSettingsManagerTest {
    @Test
    fun summarizesManagedMediaDatabasePartialsAndCacheAndCleansOnlyConfirmedClass() {
        val root = Files.createTempDirectory("storage-settings").toFile()
        val files = root.resolve("files").apply { mkdirs() }
        val cache = root.resolve("cache").apply { mkdirs() }
        files.resolve("offline-yt-player.sqlite3").writeBytes(ByteArray(11))
        files.resolve("items/video.mp4").apply { parentFile?.mkdirs(); writeBytes(ByteArray(23)) }
        files.resolve("items/.video.mp4.partial").writeBytes(ByteArray(7))
        files.resolve("items/.video.mp4.partial.resume.json").writeBytes(ByteArray(5))
        cache.resolve("thumb.bin").writeBytes(ByteArray(13))
        val manager = StorageSettingsManager.forTest(files, cache, freeBytes = 101)

        val summary = manager.summarize()
        assertEquals(23, summary.mediaBytes)
        assertEquals(11, summary.databaseBytes)
        assertEquals(12, summary.partialBytes)
        assertEquals(13, summary.cacheBytes)
        assertEquals(101, summary.freeBytes)

        assertEquals(12, manager.cleanup(ManagedCleanup.Incomplete))
        assertFalse(files.resolve("items/.video.mp4.partial").exists())
        assertEquals(23, files.resolve("items/video.mp4").length())
        assertEquals(13, manager.cleanup(ManagedCleanup.Cache))
        root.deleteRecursively()
    }
}
