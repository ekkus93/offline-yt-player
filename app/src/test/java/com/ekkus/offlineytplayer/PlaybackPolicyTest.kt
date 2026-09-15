package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy
import com.ekkus.offlineytplayer.ui.PlayerLayoutPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun completedPlaybackCannotUseNetworkUris() {
        assertFalse(LocalPlaybackPolicy.UsesNetworkUris)
        val failure = runCatching {
            LocalPlaybackPolicy.validate(LocalPlaybackAsset("https://example.invalid/video.mp4", title = "Remote"))
        }
        assertTrue(failure.isFailure)
    }

    @Test
    fun portraitPlayerHasFixedNonScrollingControls() {
        assertEquals(16f / 9f, PlayerLayoutPolicy.VideoAspectRatio)
        assertEquals(3, PlayerLayoutPolicy.PrimaryTransportActions)
        assertEquals(3, PlayerLayoutPolicy.SecondaryControlActions)
        assertFalse(PlayerLayoutPolicy.HasScrollingControls)
        assertFalse(PlayerLayoutPolicy.HasLandscapeAction)
    }

    @Test
    fun nearEndThresholdMarksPlaybackComplete() {
        assertFalse(LocalPlaybackPolicy.completedByPosition(60_000, 120_000))
        assertTrue(LocalPlaybackPolicy.completedByPosition(91_000, 120_000))
    }
}
