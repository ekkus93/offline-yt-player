package com.ekkus.offlineytplayer.ui

internal object SettingsHubPolicy {
    val rows = listOf("Downloads", "Playback", "Storage", "Appearance", "About")

    const val RowCount = 5
    const val RowHeightDp = 56
    const val HeaderHeightDp = 64
    const val BottomNavigationHeightDp = 80
    const val VerticalPaddingDp = 24

    fun requiredHeightDp(): Int =
        HeaderHeightDp + (RowCount * RowHeightDp) + BottomNavigationHeightDp + VerticalPaddingDp

    fun fitsWithoutScrolling(availableHeightDp: Int): Boolean =
        availableHeightDp >= requiredHeightDp()
}
