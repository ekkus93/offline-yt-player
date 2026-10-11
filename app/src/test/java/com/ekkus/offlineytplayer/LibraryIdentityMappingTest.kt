package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.*
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIdentityMappingTest {
    private fun item(id: String) = CoreLibraryItem(id, CoreSourceIdentity("fixture", id, null), "Fixture", 1000, "720p", 1, 0, true)
    private fun asset(id: String) = CoreLibraryPlaybackAsset(id, "fixture.mp4", null, true, null)
    private fun map(items: List<CoreLibraryItem>, assets: List<CoreLibraryPlaybackAsset>) =
        CoreGatewayResult(value = items, error = null).toLibraryScreenState(
            File("."), CoreGatewayResult(value = assets, error = null),
        )

    @Test fun duplicateLibraryIdsFailClosed() {
        assertTrue(map(listOf(item("same"), item("same")), listOf(asset("same"))) is LibraryScreenState.Failed)
    }

    @Test fun blankLibraryIdsFailClosed() {
        assertTrue(map(listOf(item(" ")), listOf(asset(" "))) is LibraryScreenState.Failed)
    }

    @Test fun duplicatePlaybackIdsFailClosed() {
        assertTrue(map(listOf(item("one")), listOf(asset("one"), asset("one"))) is LibraryScreenState.Failed)
    }

    @Test fun blankPlaybackIdsFailClosed() {
        assertTrue(map(listOf(item("one")), listOf(asset("one"), asset(" "))) is LibraryScreenState.Failed)
    }

    @Test fun relativeTraversalPlaybackPathFailsClosed() {
        val escaped = CoreLibraryPlaybackAsset("one", "../outside.mp4", null, true, null)
        assertTrue(map(listOf(item("one")), listOf(escaped)) is LibraryScreenState.Failed)
    }

    @Test fun absolutePlaybackPathFailsClosed() {
        val escaped = CoreLibraryPlaybackAsset("one", "/tmp/outside.mp4", null, true, null)
        assertTrue(map(listOf(item("one")), listOf(escaped)) is LibraryScreenState.Failed)
    }

    @Test fun symlinkEscapingLibraryRootFailsClosed() {
        val root = java.nio.file.Files.createTempDirectory("playback-root").toFile()
        val outside = java.nio.file.Files.createTempFile("playback-outside", ".mp4").toFile()
        val link = File(root, "link.mp4")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
            val result = CoreGatewayResult(value = listOf(item("one")), error = null)
                .toLibraryScreenState(root, CoreGatewayResult(value = listOf(
                    CoreLibraryPlaybackAsset("one", "link.mp4", null, true, null),
                ), error = null))
            assertTrue(result is LibraryScreenState.Failed)
        } finally {
            link.delete()
            outside.delete()
            root.delete()
        }
    }

    @Test fun validNestedPlaybackPathStaysWithinLibraryRoot() {
        val root = java.nio.file.Files.createTempDirectory("playback-root").toFile()
        try {
            val path = "items/one/video.mp4"
            val result = CoreGatewayResult(value = listOf(item("one")), error = null)
                .toLibraryScreenState(root, CoreGatewayResult(value = listOf(
                    CoreLibraryPlaybackAsset("one", path, null, true, null),
                ), error = null))
            assertTrue(result is LibraryScreenState.Ready)
            assertTrue(safeLocalPlaybackPath(root, path) == File(root, path).canonicalPath)
        } finally {
            root.delete()
        }
    }

    @Test fun validDistinctIdsRemainReady() {
        assertTrue(map(listOf(item("one")), listOf(asset("one"))) is LibraryScreenState.Ready)
    }
}
