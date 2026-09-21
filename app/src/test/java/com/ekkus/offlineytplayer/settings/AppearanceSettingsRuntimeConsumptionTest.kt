package com.ekkus.offlineytplayer.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsRuntimeConsumptionTest {
    @Test
    fun appearanceAndLibraryLayoutAreDurableAndConsumedByUi() {
        val store = File("src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt").readText()
        val shell = File("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt").readText()
        val library = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(store.contains("putString(Keys.Appearance, next.appearance.name)"))
        assertTrue(store.contains("putString(Keys.LibraryLayout, next.libraryLayout.name)"))
        assertTrue(shell.contains("OfflineYTPlayerTheme(settingsSnapshot.appearance)"))
        assertTrue(shell.contains("onUpdateSettings { appearance = nextAppearanceSetting(settings.appearance) }"))
        assertTrue(library.contains("settings.libraryLayout"))
        assertTrue(library.contains("onUpdateSettings { libraryLayout ="))
    }
}
