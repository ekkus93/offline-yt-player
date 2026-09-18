package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsPolicyTest {
    @Test
    fun darkModeIsTheDeterministicDefault() {
        val settings = AppearanceSettings()
        assertEquals(AppearanceMode.Dark, settings.mode)
        assertEquals(LibraryLayout.List, settings.libraryLayout)
    }

    @Test
    fun selectorsExposeOnlyRequiredBoundedChoices() {
        assertEquals(
            listOf(AppearanceMode.Dark, AppearanceMode.Light, AppearanceMode.System),
            AppearanceSettingsPolicy.Modes,
        )
        assertEquals(
            listOf(LibraryLayout.List, LibraryLayout.Grid),
            AppearanceSettingsPolicy.LibraryLayouts,
        )
    }
}
