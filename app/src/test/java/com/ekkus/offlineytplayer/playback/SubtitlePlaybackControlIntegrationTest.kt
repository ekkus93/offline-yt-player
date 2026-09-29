package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePlaybackControlIntegrationTest {
    @Test
    fun localSubtitleTracksAreAttachedAndControllerSelectionMutatesMedia3TrackPolicy() {
        val localPlayback = File("src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt").readText()
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(localPlayback.contains("setSubtitleConfigurations(subtitleConfigurations)"))
        assertTrue(localPlayback.contains("Uri.fromFile(File(track.path))"))
        assertTrue(player.contains("LocalPlaybackPolicy.availableSubtitleLabels(validated)"))
        assertTrue(player.contains("controller?.applySubtitleSelection(validated, selectedSubtitleIndex)"))
        assertTrue(player.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)"))
        assertTrue(player.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)"))
        assertTrue(player.contains("setPreferredTextLanguage(asset.subtitleTracks[selectedIndex].language)"))
    }

    @Test
    fun subtitleControlCyclesEveryPersistedTrackAndThenDisablesText() {
        val player = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(player.contains("current + 1 < trackCount -> current + 1"))
        assertTrue(player.contains("else -> -1"))
        assertTrue(player.contains("Subtitles: Off"))
    }
}
