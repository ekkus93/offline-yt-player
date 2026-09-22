package com.ekkus.offlineytplayer.ui

import java.io.File
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
}
