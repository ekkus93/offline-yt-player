package com.ekkus.offlineytplayer.ui

internal enum class DownloadVisualState {
    Active,
    Paused,
    Failed,
    Completed,
}

internal data class DownloadsScreenPolicy(
    val fixedAppBar: Boolean = true,
    val fixedFilterControls: Boolean = true,
    val fixedBottomNavigation: Boolean = true,
    val transferListScrolls: Boolean = true,
    val wholeScreenScrolls: Boolean = false,
) {
    fun supports(state: DownloadVisualState): Boolean = state in DownloadVisualState.entries

    fun fixedChromeIsPreserved(): Boolean =
        fixedAppBar && fixedFilterControls && fixedBottomNavigation &&
            transferListScrolls && !wholeScreenScrolls
}
