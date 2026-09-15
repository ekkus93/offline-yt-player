package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.ui.CollectionLayoutPolicy
import com.ekkus.offlineytplayer.ui.DownloadUiState
import com.ekkus.offlineytplayer.ui.LibraryLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionLayoutTest {
    @Test
    fun collectionScreensKeepControlsOutsideScrollableLists() {
        assertEquals(2, CollectionLayoutPolicy.LibraryFixedControlRows)
        assertEquals(1, CollectionLayoutPolicy.DownloadFixedControlRows)
        assertFalse(CollectionLayoutPolicy.HasHorizontalControlScrolling)
    }

    @Test
    fun collectionActionsMeetMinimumTouchTarget() {
        assertTrue(CollectionLayoutPolicy.MinimumActionHeightDp >= 48)
    }

    @Test
    fun libraryAndDownloadStatesCoverRequiredUx() {
        assertEquals(listOf("List", "Grid"), LibraryLayout.entries.map { it.name })
        assertEquals(listOf("Active", "Paused", "Failed", "Completed"), DownloadUiState.entries.map { it.name })
    }
}
