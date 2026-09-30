package com.ekkus.offlineytplayer.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStoreInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun clearSettings() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun sharedPreferencesSettingsPersistTypedSnapshotAcrossReopen() {
        clearSettings()

        val observed = mutableListOf<AppSettingsSnapshot>()
        SharedPreferencesAppSettingsStore.open(context).use { store ->
            val subscription = store.observe { observed += it }
            val updated = store.update {
                defaultQuality = "Audio only"
                wifiOnlyDownloads = false
                maxConcurrentDownloads = AppSettingsDefaults.MaxConcurrentDownloadsUpperBound + 20
                subtitleDefault = "None"
                rememberPlaybackPosition = false
                playbackSpeed = 1.5f
                appearance = AppearanceSetting.Dark
                libraryLayout = LibraryLayoutSetting.Grid
            }
            subscription.close()

            assertEquals("Audio only", updated.defaultQuality)
            assertEquals(false, updated.wifiOnlyDownloads)
            assertEquals(AppSettingsDefaults.MaxConcurrentDownloadsUpperBound, updated.maxConcurrentDownloads)
            assertEquals("None", updated.subtitleDefault)
            assertEquals(false, updated.rememberPlaybackPosition)
            assertEquals(1.5f, updated.playbackSpeed, 0.0f)
            assertEquals(AppearanceSetting.Dark, updated.appearance)
            assertEquals(LibraryLayoutSetting.Grid, updated.libraryLayout)
        }

        SharedPreferencesAppSettingsStore.open(context).use { reopened ->
            val snapshot = reopened.snapshot()
            assertEquals(APP_SETTINGS_SCHEMA_VERSION, snapshot.schemaVersion)
            assertEquals("Audio only", snapshot.defaultQuality)
            assertEquals(false, snapshot.wifiOnlyDownloads)
            assertEquals(AppSettingsDefaults.MaxConcurrentDownloadsUpperBound, snapshot.maxConcurrentDownloads)
            assertEquals("None", snapshot.subtitleDefault)
            assertEquals(false, snapshot.rememberPlaybackPosition)
            assertEquals(1.5f, snapshot.playbackSpeed, 0.0f)
            assertEquals(AppearanceSetting.Dark, snapshot.appearance)
            assertEquals(LibraryLayoutSetting.Grid, snapshot.libraryLayout)
        }

        assertTrue(observed.isNotEmpty())
        assertEquals(AppSettingsDefaults.DefaultQuality, observed.first().defaultQuality)
        assertEquals("Audio only", observed.last().defaultQuality)
    }

    @Test
    fun sharedPreferencesSettingsFallBackToSafeDefaultsForInvalidStoredEnums() {
        clearSettings()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("appearance.theme", "Blue")
            .putString("appearance.library_layout", "Carousel")
            .putInt("download.max_concurrent", 0)
            .commit()

        SharedPreferencesAppSettingsStore.open(context).use { store ->
            val snapshot = store.snapshot()
            assertEquals(AppearanceSetting.System, snapshot.appearance)
            assertEquals(LibraryLayoutSetting.List, snapshot.libraryLayout)
            assertEquals(1, snapshot.maxConcurrentDownloads)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "offline_yt_player_settings"
    }
}
