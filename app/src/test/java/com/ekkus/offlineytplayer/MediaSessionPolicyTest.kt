package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.playback.PlaybackSessionPolicy
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionPolicyTest {
    @Test
    fun playbackSessionSupportsRequiredSystemControls() {
        assertTrue(PlaybackSessionPolicy.SupportsLockScreenControls)
        assertTrue(PlaybackSessionPolicy.SupportsHeadsetControls)
        assertTrue(PlaybackSessionPolicy.HandlesAudioFocus)
        assertTrue(PlaybackSessionPolicy.HandlesAudioBecomingNoisy)
    }

    @Test
    fun manifestDeclaresMediaPlaybackForegroundService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("androidx.media3.session.MediaSessionService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaPlayback\""))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
    }
}
