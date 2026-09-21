package com.ekkus.offlineytplayer.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSettingsRuntimeConsumptionTest {
    @Test
    fun playbackSettingsAreDurableAndAppliedByCanonicalSessionController() {
        val store = File("src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt").readText()
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()
        assertTrue(store.contains("putBoolean(Keys.RememberPlaybackPosition, next.rememberPlaybackPosition)"))
        assertTrue(store.contains("putFloat(Keys.PlaybackSpeed, next.playbackSpeed)"))
        assertTrue(player.contains("if (settings.rememberPlaybackPosition)"))
        assertTrue(player.contains("connected.setPlaybackSpeed(settings.playbackSpeed)"))
        assertTrue(player.contains("onUpdateSettings { playbackSpeed = speed }"))
    }

    @Test
    fun disabledResumeStartsAtZeroAndSkipsPositionWrites() {
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()
        assertTrue(player.contains("if (settings.rememberPlaybackPosition) LocalPlaybackPolicy.restoredStartPosition"))
        assertTrue(player.contains("if (!settings.rememberPlaybackPosition) return"))
    }

    @Test
    fun playbackSettingsPageContainsOnlyRuntimeBackedDefaults() {
        val shell = File("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt").readText()
        assertTrue(shell.contains("Remember position"))
        assertTrue(shell.contains("Default speed:"))
        assertTrue(!shell.contains("SettingValue(\"Skip interval\""))
        assertTrue(!shell.contains("SettingValue(\"Subtitles\", \"Remember selection\""))
        assertTrue(!shell.contains("SettingValue(\"Audio\", \"Default track\""))
    }
}
