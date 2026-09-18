package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadSettingsPolicyTest {
    @Test
    fun defaultsCoverRequiredDownloadPreferences() {
        val settings = DownloadSettings()
        assertEquals(DefaultQuality.BestCompatible, settings.defaultQuality)
        assertFalse(settings.wifiOnly)
        assertEquals(2, settings.concurrentDownloadLimit)
        assertEquals(SubtitleDefault.Off, settings.subtitleDefault)
        assertEquals(RetryPreference.Automatic, settings.retryPreference)
    }

    @Test
    fun concurrentDownloadPreferenceIsBounded() {
        assertEquals(1, DownloadSettingsPolicy.normalizeConcurrentLimit(0))
        assertEquals(2, DownloadSettingsPolicy.normalizeConcurrentLimit(2))
        assertEquals(3, DownloadSettingsPolicy.normalizeConcurrentLimit(99))
    }
}
