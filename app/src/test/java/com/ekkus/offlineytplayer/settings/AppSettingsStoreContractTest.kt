package com.ekkus.offlineytplayer.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStoreContractTest {
    @Test
    fun durableSettingsUseDocumentedSharedPreferencesStore() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt").readText()
        assertTrue(source.contains("SharedPreferences"))
        assertTrue(source.contains("offline_yt_player_settings"))
        assertTrue(source.contains("APP_SETTINGS_SCHEMA_VERSION = 1"))
    }

    @Test
    fun settingsExposeTypedSnapshotMutationAndObservation() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt").readText()
        assertTrue(source.contains("data class AppSettingsSnapshot"))
        assertTrue(source.contains("class AppSettingsMutation"))
        assertTrue(source.contains("fun observe(observer: (AppSettingsSnapshot) -> Unit): SettingsSubscription"))
        assertTrue(source.contains("enum class AppearanceSetting"))
        assertTrue(source.contains("enum class LibraryLayoutSetting"))
    }

    @Test
    fun settingsHaveDefaultsAndMigrationBoundary() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt").readText()
        assertTrue(source.contains("object AppSettingsDefaults"))
        assertTrue(source.contains("ensureSchemaVersion()"))
        assertTrue(source.contains("putInt(Keys.SchemaVersion, APP_SETTINGS_SCHEMA_VERSION)"))
        assertTrue(source.contains("coerceIn(1, AppSettingsDefaults.MaxConcurrentDownloadsUpperBound)"))
    }
}
