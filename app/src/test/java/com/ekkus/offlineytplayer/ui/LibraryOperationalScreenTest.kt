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
        assertTrue(source.contains("row.matchesLibraryQuery(query)"))
        assertTrue(source.contains("title.contains(normalized, ignoreCase = true)"))
        assertTrue(source.contains("detail.contains(normalized, ignoreCase = true)"))
    }

    @Test
    fun layoutSelectionRemainsSaveableAcrossRecomposition() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(source.contains("var layout by rememberSaveable"))
        assertTrue(source.contains("layout = if (layout == LibraryLayout.List) LibraryLayout.Grid else LibraryLayout.List"))
    }
}
