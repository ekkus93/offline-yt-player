package com.ekkus.offlineytplayer.settings

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSettingsRuntimeConsumptionTest {
    @Test
    fun storageSummaryAndCleanupOperateOnRealAppOwnedFiles() {
        val root = Files.createTempDirectory("storage-settings-runtime").toFile()
        val files = root.resolve("files").apply { mkdirs() }
        val cache = root.resolve("cache").apply { mkdirs() }
        try {
            val database = files.resolve("offline-yt-player.sqlite3").apply { writeBytes(ByteArray(8)) }
            val partial = files.resolve("transfer.partial").apply { writeBytes(ByteArray(6)) }
            val media = files.resolve("video.mp4").apply { writeBytes(ByteArray(10)) }
            val cached = cache.resolve("thumb.png").apply { writeBytes(ByteArray(5)) }
            val manager: ManagedStorageOperations =
                StorageSettingsManager.forTest(files, cache, freeBytes = 4096)

            assertEquals(
                ManagedStorageSummary(
                    mediaBytes = 10,
                    databaseBytes = 8,
                    partialBytes = 6,
                    cacheBytes = 5,
                    freeBytes = 4096,
                ),
                manager.summarize(),
            )

            assertEquals(5L, manager.cleanup(ManagedCleanup.Cache))
            assertFalse(cached.exists())
            assertTrue(database.exists())
            assertTrue(partial.exists())
            assertTrue(media.exists())

            assertEquals(6L, manager.cleanup(ManagedCleanup.Incomplete))
            assertFalse(partial.exists())
            assertTrue(database.exists())
            assertTrue(media.exists())
            assertEquals(
                ManagedStorageSummary(
                    mediaBytes = 10,
                    databaseBytes = 8,
                    partialBytes = 0,
                    cacheBytes = 0,
                    freeBytes = 4096,
                ),
                manager.summarize(),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
