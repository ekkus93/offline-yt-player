package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionPolicyTest {
    @Test
    fun mediaSessionPolicyCoversPlatformPlaybackControls() {
        assertTrue(PlaybackSessionPolicy.SupportsLockScreenControls)
        assertTrue(PlaybackSessionPolicy.SupportsHeadsetControls)
        assertTrue(PlaybackSessionPolicy.HandlesAudioFocus)
        assertTrue(PlaybackSessionPolicy.HandlesAudioBecomingNoisy)
    }

    @Test
    fun manifestRegistersMediaSessionServiceForPlatformControllers() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".playback.PlaybackSessionService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaPlayback\""))
        assertTrue(manifest.contains("androidx.media3.session.MediaSessionService"))
    }

    @Test
    fun serviceDelegatesAudioFocusAndNoisyHandlingToMedia3Player() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()
        assertTrue(service.contains("setAudioAttributes(AudioAttributes.DEFAULT, true)"))
        assertTrue(service.contains("setHandleAudioBecomingNoisy(true)"))
        assertTrue(service.contains("MediaSession.Builder(this, player).build()"))
    }
}
