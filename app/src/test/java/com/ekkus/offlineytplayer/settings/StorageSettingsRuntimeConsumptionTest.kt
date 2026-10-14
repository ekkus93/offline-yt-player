package com.ekkus.offlineytplayer.settings

import java.nio.file.Files
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSettingsRuntimeConsumptionTest {
    @Test
    fun deepCacheCleanupUsesPostorderWithoutFollowingSymlinks() {
        val root = Files.createTempDirectory("storage-settings-deep-cache").toFile()
        val external = Files.createTempDirectory("storage-settings-cache-external").toFile()
        val files = root.resolve("files").apply { mkdirs() }
        val cache = root.resolve("cache").apply { mkdirs() }
        try {
            var nested = cache
            repeat(256) {
                nested = nested.resolve("d").apply { mkdir() }
            }
            nested.resolve("thumbnail.bin").writeBytes(ByteArray(23))
            val externalAsset = external.resolve("untouched.bin").apply { writeBytes(ByteArray(11)) }
            Files.createSymbolicLink(nested.resolve("outside").toPath(), externalAsset.toPath())
            val manager = StorageSettingsManager.forTest(files, cache)
            assertEquals(23L, manager.summarize().cacheBytes)
            assertEquals(23L, manager.cleanup(ManagedCleanup.Cache))
            assertTrue("External symlink target must not be deleted", externalAsset.exists())
            assertTrue("Cache root must remain available", cache.isDirectory)
            assertTrue("Cache must be empty after successful cleanup", cache.listFiles()?.isEmpty() == true)
            assertEquals(0L, manager.summarize().cacheBytes)
        } finally {
            root.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun deepManagedTreeIsCountedWithoutRecursiveTraversalOrFollowingSymlinks() {
        val root = Files.createTempDirectory("storage-settings-deep-tree").toFile()
        val external = Files.createTempDirectory("storage-settings-deep-external").toFile()
        val files = root.resolve("files").apply { mkdirs() }
        val cache = root.resolve("cache").apply { mkdirs() }
        try {
            var nested = files
            repeat(256) {
                nested = nested.resolve("d").apply { mkdir() }
            }
            val deepPartial = nested.resolve("transfer.partial").apply { writeBytes(ByteArray(17)) }
            val deepMedia = nested.resolve("movie.mp4").apply { writeBytes(ByteArray(31)) }
            val externalAsset = external.resolve("external.partial").apply { writeBytes(ByteArray(19)) }
            Files.createSymbolicLink(nested.resolve("linked.partial").toPath(), externalAsset.toPath())
            val manager = StorageSettingsManager.forTest(files, cache, freeBytes = 1024)
            assertEquals(
                ManagedStorageSummary(31, 0, 17, 0, 1024),
                manager.summarize(),
            )
            assertEquals(17L, manager.cleanup(ManagedCleanup.Incomplete))
            assertFalse(deepPartial.exists())
            assertTrue(deepMedia.exists())
            assertTrue("External symlink target must remain intact", externalAsset.exists())
            assertEquals(0L, manager.summarize().partialBytes)
        } finally {
            root.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun storageSummaryRejectsSymlinkedManagedRootsInsteadOfReportingMisleadingZeros() {
        val root = Files.createTempDirectory("storage-settings-root-guard").toFile()
        val external = Files.createTempDirectory("storage-settings-external").toFile()
        val files = root.resolve("files").apply { mkdirs() }
        val cache = root.resolve("cache").apply { mkdirs() }
        val filesLink = root.resolve("files-link").toPath()
        val cacheLink = root.resolve("cache-link").toPath()
        val externalAsset = external.resolve("private-media.bin").apply { writeBytes(ByteArray(8)) }
        try {
            Files.createSymbolicLink(filesLink, external.toPath())
            Files.createSymbolicLink(cacheLink, external.toPath())
            val filesError = runCatching {
                StorageSettingsManager.forTest(filesLink.toFile(), cache).summarize()
            }.exceptionOrNull()
            val cacheError = runCatching {
                StorageSettingsManager.forTest(files, cacheLink.toFile()).summarize()
            }.exceptionOrNull()
            assertTrue("Symlinked files root must fail closed", filesError is IOException)
            assertTrue("Symlinked cache root must fail closed", cacheError is IOException)
            assertTrue("External media must remain untouched", externalAsset.exists())
        } finally {
            Files.deleteIfExists(filesLink)
            Files.deleteIfExists(cacheLink)
            root.deleteRecursively()
            external.deleteRecursively()
        }
    }

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
