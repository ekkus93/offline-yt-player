package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.ui.DownloadUiState
import com.ekkus.offlineytplayer.ui.LibraryLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionStateModelTest {
    @Test
    fun libraryLayoutAndDownloadStateModelsExposeTheirSupportedValues() {
        assertEquals(listOf("List", "Grid"), LibraryLayout.entries.map { it.name })
        assertEquals(listOf("Active", "Paused", "Failed", "Completed", "Canceled"), DownloadUiState.entries.map { it.name })
    }
}
