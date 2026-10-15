package com.ekkus.offlineytplayer.settings

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.ui.SettingsPage
import com.ekkus.offlineytplayer.ui.SettingsSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the production [SettingsPage] against the real durable
 * [SharedPreferencesAppSettingsStore]: a control change must reach the store,
 * flow back into the rendered page, and survive reopening the store.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPagesRuntimeInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: SharedPreferencesAppSettingsStore
    private var subscription: SettingsSubscription? = null

    @Before
    fun openFreshStore() {
        clearPreferences()
        store = SharedPreferencesAppSettingsStore.open(context)
    }

    @After
    fun closeStore() {
        subscription?.close()
        store.close()
        clearPreferences()
    }

    @Test
    fun downloadSettingsControlsPersistAndRenderTheStoredValues() {
        showPage(SettingsSection.Downloads)

        compose.onNodeWithText("Default quality: Best compatible").performClick()
        compose.onNodeWithText("Default quality: Audio only").assertIsDisplayed()

        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.onNode(isToggleable()).assertIsOff()

        compose.onNodeWithText("Concurrent downloads: ${AppSettingsDefaults.MaxConcurrentDownloads}").performClick()
        compose.onNodeWithText("Concurrent downloads: ${AppSettingsDefaults.MaxConcurrentDownloads + 1}").assertIsDisplayed()

        compose.onNodeWithText("Subtitles: Preferred language").performClick()
        compose.onNodeWithText("Subtitles: None").assertIsDisplayed()

        // Retry is intentionally an automatic runtime policy, not a persisted control.
        compose.onNodeWithText("Automatic runtime policy").assertIsDisplayed()

        val reopened = reopenedSnapshot()
        assertEquals("Audio only", reopened.defaultQuality)
        assertFalse(reopened.wifiOnlyDownloads)
        assertEquals(AppSettingsDefaults.MaxConcurrentDownloads + 1, reopened.maxConcurrentDownloads)
        assertEquals("None", reopened.subtitleDefault)
    }

    @Test
    fun playbackSettingsExposeOnlyRuntimeBackedControlsAndPersist() {
        showPage(SettingsSection.Playback)

        compose.onNodeWithText("Remember position").assertIsDisplayed()
        compose.onNodeWithText("Default speed: 1.0×").assertIsDisplayed()
        // Decorative, runtime-unbacked controls must not be offered.
        listOf("Skip interval", "Remember selection", "Default track").forEach { label ->
            compose.onNodeWithText(label, substring = true).assertDoesNotExist()
        }

        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("Default speed: 1.0×").performClick()
        compose.onNodeWithText("Default speed: 1.25×").assertIsDisplayed()

        val reopened = reopenedSnapshot()
        assertFalse(reopened.rememberPlaybackPosition)
        assertEquals(1.25f, reopened.playbackSpeed, 0.0f)
    }

    @Test
    fun appearanceSettingsCycleThemeAndLibraryLayoutAndPersist() {
        showPage(SettingsSection.Appearance)

        compose.onNodeWithText("Theme: System").performClick()
        compose.onNodeWithText("Theme: Light").assertIsDisplayed()
        compose.onNodeWithText("Library layout: List").performClick()
        compose.onNodeWithText("Library layout: Grid").assertIsDisplayed()

        val reopened = reopenedSnapshot()
        assertEquals(AppearanceSetting.Light, reopened.appearance)
        assertEquals(LibraryLayoutSetting.Grid, reopened.libraryLayout)
    }

    private fun showPage(section: SettingsSection) {
        var snapshot by mutableStateOf(store.snapshot())
        subscription = store.observe { snapshot = it }
        compose.setContent {
            SettingsPage(
                padding = PaddingValues(),
                section = section,
                settings = snapshot,
                onUpdateSettings = { mutation -> store.update(mutation) },
                onBack = {},
            )
        }
    }

    private fun reopenedSnapshot(): AppSettingsSnapshot =
        SharedPreferencesAppSettingsStore.open(context).use { it.snapshot() }

    private fun clearPreferences() {
        context.getSharedPreferences("offline_yt_player_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
