package com.ekkus.offlineytplayer.coregateway

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPlaybackGatewayBoundaryTest {
    @Test
    fun generatedGatewayTargetsDedicatedPlaybackServiceAndSafeDescriptors() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/coregateway/AppLibraryPlaybackGateway.kt").readText()
        assertTrue(source.contains("FfiLibraryPlaybackService"))
        assertTrue(source.contains("libraryPlaybackAssets"))
        assertTrue(source.contains("videoRelativePath"))
        assertTrue(source.contains("audioRelativePath"))
        assertTrue(source.contains("unavailableReason"))
    }

    @Test
    fun mainActivityCombinesLibraryRowsWithPlaybackDescriptors() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()
        assertTrue(source.contains("GeneratedUniffiLibraryPlaybackGateway.open(databasePath)"))
        assertTrue(source.contains("playbackByItemId[item.itemId]"))
        assertTrue(source.contains("videoRelativePath?.let { libraryRoot.resolve(it).absolutePath }"))
        assertTrue(source.contains("audioRelativePath?.let { libraryRoot.resolve(it).absolutePath }"))
        assertTrue(source.contains("item.completed && playback?.playable == true"))
    }
}
