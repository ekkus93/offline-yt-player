package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSettingsPolicyTest {
    @Test
    fun defaultsCoverRequiredPlaybackPreferences() {
        val settings = PlaybackSettings()
        assertTrue(settings.rememberPosition)
        assertEquals(1.0f, settings.defaultSpeed)
        assertEquals(10, settings.skipIntervalSeconds)
        assertEquals(PlaybackSubtitleDefault.Off, settings.subtitleDefault)
        assertEquals(PlaybackAudioDefault.Original, settings.audioDefault)
    }

    @Test
    fun playbackSpeedAndSkipIntervalAreBounded() {
        assertEquals(0.5f, PlaybackSettingsPolicy.normalizeSpeed(0.1f))
        assertEquals(1.25f, PlaybackSettingsPolicy.normalizeSpeed(1.25f))
        assertEquals(2.0f, PlaybackSettingsPolicy.normalizeSpeed(9.0f))
        assertEquals(5, PlaybackSettingsPolicy.normalizeSkipInterval(1))
        assertEquals(15, PlaybackSettingsPolicy.normalizeSkipInterval(16))
        assertEquals(30, PlaybackSettingsPolicy.normalizeSkipInterval(99))
    }
}
