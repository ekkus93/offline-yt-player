package com.ekkus.offlineytplayer.settings

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun incompleteCleanupStreamsLargeInterruptedQueueAndPreservesCompletedMedia() {
        val root = Files.createTempDirectory("storage-many-partials").toFile()
        try {
            val files = root.resolve("files").apply { mkdirs() }
            val cache = root.resolve("cache").apply { mkdirs() }
            val itemDir = files.resolve("items").apply { mkdirs() }
            repeat(512) { index ->
                itemDir.resolve("item-$index.partial").writeBytes(byteArrayOf(1))
                itemDir.resolve("item-$index.partial.resume.json").writeBytes(byteArrayOf(2, 3))
            }
            val completed = itemDir.resolve("completed.mp4").apply { writeBytes(ByteArray(19)) }
            val manager = StorageSettingsManager.forTest(files, cache)
            assertEquals(512L * 3L, manager.summarize().partialBytes)
            assertEquals(512L * 3L, manager.cleanup(ManagedCleanup.Incomplete))
            assertEquals(0L, manager.summarize().partialBytes)
            assertEquals(19L, completed.length())
            assertEquals(0L, manager.cleanup(ManagedCleanup.Incomplete))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheCleanupUnlinksNestedSymlinksWithoutReadingOrDeletingExternalData() {
        val root = Files.createTempDirectory("storage-symlinks").toFile()
        val external = Files.createTempDirectory("storage-external").toFile()
        try {
            val files = root.resolve("files").apply { mkdirs() }
            val cache = root.resolve("cache").apply { mkdirs() }
            val nested = cache.resolve("nested").apply { mkdirs() }
            nested.resolve("owned.bin").writeBytes(ByteArray(13))
            external.resolve("private.bin").writeBytes(ByteArray(300))
            Files.createSymbolicLink(nested.resolve("external").toPath(), external.toPath())
            val manager = StorageSettingsManager.forTest(files, cache)

            assertEquals(13, manager.summarize().cacheBytes)
            assertEquals(13, manager.cleanup(ManagedCleanup.Cache))
            assertTrue(external.resolve("private.bin").isFile)
            assertEquals(300, external.resolve("private.bin").length())
            assertFalse(nested.exists())
            assertEquals(0, manager.summarize().cacheBytes)
        } finally {
            root.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun incompleteCleanupDeletesOnlyOwnedPartialsAndUnlinksExternalSymlink() {
        val root = Files.createTempDirectory("storage-incomplete-links").toFile()
        val external = Files.createTempDirectory("storage-outside-partial").toFile()
        try {
            val files = root.resolve("files").apply { mkdirs() }
            val cache = root.resolve("cache").apply { mkdirs() }
            val itemDir = files.resolve("items").apply { mkdirs() }
            itemDir.resolve("owned.partial").writeBytes(ByteArray(7))
            val externalPartial = external.resolve("important.partial").apply { writeBytes(ByteArray(300)) }
            val shortcut = itemDir.resolve("external.partial")
            Files.createSymbolicLink(shortcut.toPath(), externalPartial.toPath())
            val manager = StorageSettingsManager.forTest(files, cache)

            assertEquals(7, manager.summarize().partialBytes)
            assertEquals(7, manager.cleanup(ManagedCleanup.Incomplete))
            assertFalse(Files.exists(shortcut.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            assertTrue(externalPartial.isFile)
            assertEquals(300, externalPartial.length())
        } finally {
            root.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun cacheCleanupRejectsSymlinkedRootWithoutDeletingExternalFiles() {
        val sandbox = Files.createTempDirectory("storage-cache-root-link").toFile()
        val external = Files.createTempDirectory("storage-cache-root-external").toFile()
        val cacheLink = sandbox.resolve("cache")
        try {
            val files = sandbox.resolve("files").apply { mkdirs() }
            external.resolve("preserve.bin").writeBytes(ByteArray(37))
            Files.createSymbolicLink(cacheLink.toPath(), external.toPath())
            val manager = StorageSettingsManager.forTest(files, cacheLink)

            val error = runCatching { manager.cleanup(ManagedCleanup.Cache) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
            assertTrue(Files.isSymbolicLink(cacheLink.toPath()))
            assertTrue(external.resolve("preserve.bin").isFile)
            assertEquals(37, external.resolve("preserve.bin").length())
        } finally {
            Files.deleteIfExists(cacheLink.toPath())
            sandbox.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun incompleteCleanupRejectsSymlinkedRootWithoutDeletingExternalPartials() {
        val sandbox = Files.createTempDirectory("storage-incomplete-root-link").toFile()
        val external = Files.createTempDirectory("storage-incomplete-root-external").toFile()
        val filesLink = sandbox.resolve("files")
        try {
            val cache = sandbox.resolve("cache").apply { mkdirs() }
            external.resolve("preserve.partial").writeBytes(ByteArray(29))
            Files.createSymbolicLink(filesLink.toPath(), external.toPath())
            val manager = StorageSettingsManager.forTest(filesLink, cache)

            val error = runCatching { manager.cleanup(ManagedCleanup.Incomplete) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
            assertTrue(Files.isSymbolicLink(filesLink.toPath()))
            assertTrue(external.resolve("preserve.partial").isFile)
            assertEquals(29, external.resolve("preserve.partial").length())
        } finally {
            Files.deleteIfExists(filesLink.toPath())
            sandbox.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun cacheCleanupStreamsWideDirectoryAndPreservesMedia() {
        val root = Files.createTempDirectory("storage-wide-cache").toFile()
        try {
            val files = root.resolve("files").apply { mkdirs() }
            val cache = root.resolve("cache").apply { mkdirs() }
            val media = files.resolve("items/completed.mp4").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(19))
            }
            repeat(768) { index ->
                cache.resolve("thumb-$index.bin").writeBytes(byteArrayOf(1, 2))
            }
            val manager = StorageSettingsManager.forTest(files, cache)
            assertEquals(1_536L, manager.summarize().cacheBytes)
            assertEquals(1_536L, manager.cleanup(ManagedCleanup.Cache))
            assertEquals(0L, manager.summarize().cacheBytes)
            assertEquals(19L, media.length())
            assertEquals(0L, manager.cleanup(ManagedCleanup.Cache))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nestedWideCacheAccountingAndCleanupPreserveMedia() {
        val root = Files.createTempDirectory("nested-cache").toFile()
        try {
            val files = root.resolve("files").apply { mkdirs() }
            val cache = root.resolve("cache").apply { mkdirs() }
            val nested = cache.resolve("nested").apply { mkdirs() }
            val media = files.resolve("completed.mp4").apply { writeBytes(ByteArray(7)) }
            repeat(512) { index ->
                nested.resolve("thumb-$index.bin").writeBytes(byteArrayOf(1, 2))
            }
            val manager = StorageSettingsManager.forTest(files, cache)
            assertEquals(1_024L, manager.summarize().cacheBytes)
            assertEquals(1_024L, manager.cleanup(ManagedCleanup.Cache))
            assertEquals(0L, manager.summarize().cacheBytes)
            assertTrue(media.isFile)
            assertEquals(0L, manager.cleanup(ManagedCleanup.Cache))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheCleanupRejectsAnUnenumerableRootInsteadOfReportingSuccess() {
        val root = Files.createTempDirectory("storage-cache-enumeration").toFile()
        try {
            val files = root.resolve("files").apply { mkdirs() }
            val invalidCacheRoot = root.resolve("cache").apply { writeText("not a directory") }
            val manager = StorageSettingsManager.forTest(files, invalidCacheRoot)
            val error = runCatching { manager.cleanup(ManagedCleanup.Cache) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
            assertTrue(invalidCacheRoot.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

}
