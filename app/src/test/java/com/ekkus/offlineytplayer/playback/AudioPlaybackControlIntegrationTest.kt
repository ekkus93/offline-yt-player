package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackControlIntegrationTest {
    @Test
    fun playerExposesAudioSelectionOnlyWhenMultiplePersistedTracksExist() {
        val localPlayback = File("src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt").readText()
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(localPlayback.contains("fun availableAudioLabels(asset: LocalPlaybackAsset): List<String>"))
        assertTrue(localPlayback.contains("fun shouldEnableAudioSelection(asset: LocalPlaybackAsset): Boolean = availableAudioLabels(asset).size > 1"))
        assertTrue(player.contains("LocalPlaybackPolicy.availableAudioLabels(validated)"))
        assertTrue(player.contains("controller != null && LocalPlaybackPolicy.shouldEnableAudioSelection(validated)"))
    }

    @Test
    fun audioSelectionCyclesTracksAndMutatesMedia3PreferredLanguage() {
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(player.contains("selectedAudioIndex = nextAudioSelection(selectedAudioIndex, audioLabels.size)"))
        assertTrue(player.contains("controller?.applyAudioSelection(validated, selectedAudioIndex)"))
        assertTrue(player.contains("private fun nextAudioSelection(current: Int, trackCount: Int): Int"))
        assertTrue(player.contains("current + 1 < trackCount -> current + 1"))
        assertTrue(player.contains("else -> 0"))
        assertTrue(player.contains("setPreferredAudioLanguage(language)"))
    }
}
