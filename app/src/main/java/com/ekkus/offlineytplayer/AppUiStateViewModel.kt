package com.ekkus.offlineytplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import com.ekkus.offlineytplayer.ui.LibraryScreenState

/**
 * Lifecycle-retained owner for production screen state.
 *
 * Repository/FFI work stays in [AppStateRefresher] and the Activity bootstrap executor;
 * this holder survives configuration recreation while the saved Activity bundle restores
 * the small query state after process recreation.
 */
internal class AppUiStateViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private companion object {
        const val SAVED_LIBRARY_QUERY = "library_query"
    }
    var libraryState by mutableStateOf<LibraryScreenState>(LibraryScreenState.Loading)
    var downloadsState by mutableStateOf<DownloadsScreenState>(DownloadsScreenState.Loading)

    @Volatile
    var libraryQuery: String? = savedStateHandle[SAVED_LIBRARY_QUERY]
        set(value) {
            field = value
            savedStateHandle[SAVED_LIBRARY_QUERY] = value
        }
}
