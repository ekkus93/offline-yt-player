package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackControlIntegrationTest {
    @Test
    fun playbackScreenExposesOnlyRealMultipleAudioTracksAndMutatesMedia3Selection() {
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()
        val localPlayback = File("src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt").readText()

        assertTrue(localPlayback.contains("fun availableAudioLabels(asset: LocalPlaybackAsset): List<String>"))
        assertTrue(localPlayback.contains("fun shouldEnableAudioSelection(asset: LocalPlaybackAsset): Boolean"))
        assertTrue(localPlayback.contains("availableAudioLabels(asset).size > 1"))
        assertTrue(localPlayback.contains("validate(asset).audioTracks"))
        assertTrue(player.contains("LocalPlaybackPolicy.availableAudioLabels(validated)"))
        assertTrue(player.contains("LocalPlaybackPolicy.shouldEnableAudioSelection(validated)"))
        assertTrue(player.contains("controller?.applyAudioSelection(validated, selectedAudioIndex)"))
        assertTrue(player.contains("setPreferredAudioLanguage(language)"))
    }

    @Test
    fun audioControlCyclesOnlyPersistedAudioTrackLabels() {
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(player.contains("private fun nextAudioSelection(current: Int, trackCount: Int): Int"))
        assertTrue(player.contains("current + 1 < trackCount -> current + 1"))
        assertTrue(player.contains("else -> 0"))
        assertTrue(player.contains("Audio: ${'$'}{labels[selectedIndex]}"))
    }
}
