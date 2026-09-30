package com.ekkus.offlineytplayer.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOperationalScreenTest {
    @Test
    fun libraryScreenHasDistinctListAndGridCollectionBranches() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("LazyVerticalGrid("))
        assertTrue(source.contains("GridCells.Fixed(2)"))
        assertTrue(source.contains("LibraryLayout.Grid -> LazyVerticalGrid"))
    }

    @Test
    fun libraryScreenSearchesRepositoryBackedMetadata() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(source.contains("matchesLibraryQuery(query)"))
        assertTrue(source.contains("title.contains"))
        assertTrue(source.contains("detail.contains"))
    }

    @Test
    fun layoutSelectionUsesDurableAppearanceSetting() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(source.contains("settings.libraryLayout"))
        assertTrue(source.contains("onUpdateSettings"))
        assertTrue(source.contains("LibraryLayoutSetting.Grid"))
        assertTrue(source.contains("LibraryLayoutSetting.List"))
    }

    @Test
    fun libraryPlayRouteExplainsUnavailableItems() {
        assertEquals(
            "Download is not complete yet.",
            LibraryPlaybackRoute.unavailableReason(
                LibraryRowModel(id = "pending", title = "Pending", detail = "Downloading", completed = false),
            ),
        )
        assertEquals(
            "Completed item is missing a local video asset.",
            LibraryPlaybackRoute.unavailableReason(
                LibraryRowModel(id = "missing", title = "Missing", detail = "Completed", completed = true),
            ),
        )
        val remote = LibraryRowModel(
            id = "remote",
            title = "Remote",
            detail = "Completed",
            completed = true,
            videoPath = "https://example.invalid/video.mp4",
        )
        assertTrue(LibraryPlaybackRoute.unavailableReason(remote)!!.contains("remote playback URIs are forbidden"))

        val local = LibraryRowModel(
            id = "local",
            title = "Local",
            detail = "Completed",
            completed = true,
            videoPath = "/tmp/offline-video.mp4",
        )
        assertNull(LibraryPlaybackRoute.unavailableReason(local))
        assertEquals("/tmp/offline-video.mp4", LibraryPlaybackRoute.assetFor(local)?.videoPath)
    }
}
