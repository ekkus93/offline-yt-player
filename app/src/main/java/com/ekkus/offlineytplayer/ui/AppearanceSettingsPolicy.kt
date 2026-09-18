package com.ekkus.offlineytplayer.ui

internal enum class AppearanceMode { Dark, Light, System }

internal data class AppearanceSettings(
    val mode: AppearanceMode = AppearanceMode.Dark,
    val libraryLayout: LibraryLayout = LibraryLayout.List,
)

internal object AppearanceSettingsPolicy {
    val Modes = listOf(AppearanceMode.Dark, AppearanceMode.Light, AppearanceMode.System)
    val LibraryLayouts = listOf(LibraryLayout.List, LibraryLayout.Grid)
}
