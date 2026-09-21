package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertFalse
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
    fun serviceOwnsSingleCanonicalPlayerAndMediaSession() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()
        assertTrue(service.contains("val player = ExoPlayer.Builder(this).build()"))
        assertTrue(service.contains("MediaSession.Builder(this, player).build()"))
        assertTrue(service.contains("override fun onGetSession"))
        assertTrue(service.contains("session.player.release()"))
        assertTrue(service.contains("session.release()"))
    }

    @Test
    fun serviceDelegatesAudioFocusAndNoisyHandlingToMedia3Player() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()
        assertTrue(service.contains("setAudioAttributes(AudioAttributes.DEFAULT, true)"))
        assertTrue(service.contains("setHandleAudioBecomingNoisy(true)"))
        assertTrue(service.contains("MediaSession.Builder(this, player).build()"))
    }

    @Test
    fun composeControlsOnlyCanonicalSessionThroughMediaController() {
        val screen = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()
        assertTrue(screen.contains("MediaController.Builder(context, token).buildAsync()"))
        assertTrue(screen.contains("connected.setMediaItem("))
        assertTrue(screen.contains("controller?.seekBack()"))
        assertTrue(screen.contains("controller?.seekForward()"))
        assertTrue(screen.contains("if (it.isPlaying) it.pause() else it.play()"))
        assertTrue(screen.contains("it.setPlaybackSpeed("))
        assertFalse(screen.contains("ExoPlayer.Builder"))
    }

    @Test
    fun playerViewAndUiCommandsShareTheSameControllerState() {
        val screen = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()
        assertTrue(screen.contains("player = controller"))
        assertTrue(screen.contains("update = { it.player = controller }"))
        assertTrue(screen.contains("connected.setMediaItem("))
        assertTrue(screen.contains("connected.prepare()"))
    }
}
