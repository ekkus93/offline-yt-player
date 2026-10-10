package com.ekkus.offlineytplayer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-retained owner for production screen state.
 *
 * Repository/FFI work stays in [AppStateRefresher] and the Activity bootstrap executor;
 * this holder survives configuration recreation while the saved Activity bundle restores
 * the small query state after process recreation.
 *
 * Keep repository state in flows rather than creating Compose MutableState during Activity
 * or ViewModel construction. Android instrumentation can launch an Activity while a test
 * snapshot is open; a state object created in that snapshot may not be readable from the
 * recomposer's snapshot until the test applies it.
 */
internal class AppUiStateViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private companion object {
        const val SAVED_LIBRARY_QUERY = "library_query"
    }

    private val mutableLibraryState = MutableStateFlow<LibraryScreenState>(LibraryScreenState.Loading)
    private val mutableDownloadsState = MutableStateFlow<DownloadsScreenState>(DownloadsScreenState.Loading)

    val libraryStateFlow: StateFlow<LibraryScreenState> = mutableLibraryState
    val downloadsStateFlow: StateFlow<DownloadsScreenState> = mutableDownloadsState

    var libraryState: LibraryScreenState
        get() = mutableLibraryState.value
        set(value) { mutableLibraryState.value = value }

    var downloadsState: DownloadsScreenState
        get() = mutableDownloadsState.value
        set(value) { mutableDownloadsState.value = value }

    @Volatile
    var libraryQuery: String? = savedStateHandle[SAVED_LIBRARY_QUERY]
        set(value) {
            field = value
            savedStateHandle[SAVED_LIBRARY_QUERY] = value
        }
}
