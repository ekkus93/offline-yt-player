package com.ekkus.offlineytplayer.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSettingsRuntimeConsumptionTest {
    @Test
    fun mainActivityOpensAndObservesDurableSettingsStore() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()
        assertTrue(source.contains("SharedPreferencesAppSettingsStore.open(this)"))
        assertTrue(source.contains("settingsSubscription = settings.observe"))
        assertTrue(source.contains("settingsSnapshot = snapshot"))
        assertTrue(source.contains("onUpdateSettings = { mutation -> settingsStore?.update(mutation) }"))
    }

    @Test
    fun addAndSettingsScreensConsumeDownloadSettings() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt").readText()
        assertTrue(source.contains("settings.defaultQuality"))
        assertTrue(source.contains("settings.wifiOnlyDownloads"))
        assertTrue(source.contains("settings.maxConcurrentDownloads"))
        assertTrue(source.contains("settings.subtitleDefault"))
        assertTrue(source.contains("downloadSettingsSummary(settings)"))
        assertTrue(source.contains("preferredQualityLabel(qualityLabels, settings.defaultQuality, analysis.qualityLabel)"))
    }

    @Test
    fun retrySettingIsNotPersistedAsDecorativeControl() {
        val settings = File("src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt").readText()
        val ui = File("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt").readText()
        assertTrue(!settings.contains("retry"))
        assertTrue(ui.contains("Retry", ignoreCase = false))
        assertTrue(ui.contains("Automatic runtime policy"))
    }
}
