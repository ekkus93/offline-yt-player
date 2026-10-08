package com.ekkus.offlineytplayer

import androidx.lifecycle.SavedStateHandle
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppUiStateViewModelTest {
    @Test
    fun retainsRepositoryPresentationAndQueryStateForActivityRecreation() {
        val holder = AppUiStateViewModel(SavedStateHandle())
        val library = LibraryScreenState.Ready(emptyList())
        val downloads = DownloadsScreenState.Ready(emptyList())

        holder.libraryState = library
        holder.downloadsState = downloads
        holder.libraryQuery = "saved query"

        assertSame(library, holder.libraryState)
        assertSame(downloads, holder.downloadsState)
        assertEquals("saved query", holder.libraryQuery)
    }
    @Test
    fun restoresLibraryQueryFromSavedStateHandleAfterProcessRecreation() {
        val holder = AppUiStateViewModel(SavedStateHandle(mapOf("library_query" to "restored query")))

        assertEquals("restored query", holder.libraryQuery)
        holder.libraryQuery = "updated query"
        assertEquals("updated query", holder.libraryQuery)
    }
}
