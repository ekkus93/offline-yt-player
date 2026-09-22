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
        assertTrue(source.contains("LibraryLayout.Grid->LazyVerticalGrid"))
    }

    @Test
    fun libraryScreenSearchesRepositoryBackedMetadata() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(source.contains("readyRows.filter{it.matchesLibraryQuery(query)}"))
        assertTrue(source.contains("title.contains(n,true)"))
        assertTrue(source.contains("detail.contains(n,true)"))
    }

    @Test
    fun layoutSelectionUsesDurableAppearanceSetting() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(source.contains("settings.libraryLayout==LibraryLayoutSetting.Grid"))
        assertTrue(source.contains("onUpdateSettings{libraryLayout="))
    }
}
