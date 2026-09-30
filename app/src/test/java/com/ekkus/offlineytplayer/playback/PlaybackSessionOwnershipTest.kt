package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionOwnershipTest {
    @Test
    fun serviceOwnsOneCanonicalPlayerAndMediaSession() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()

        assertTrue(source.contains("class PlaybackSessionService : MediaSessionService()"))
        assertTrue(source.contains("private var mediaSession: MediaSession? = null"))
        assertTrue(source.contains("val player = ExoPlayer.Builder(this)"))
        assertTrue(source.contains("setMediaSourceFactory("))
        assertTrue(source.contains("SplitAudioMediaSourceFactory(DefaultMediaSourceFactory(this))"))
        assertTrue(source.contains("mediaSession = MediaSession.Builder(this, player).build()"))
        assertTrue(source.contains("override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession"))
    }

    @Test
    fun canonicalPlayerOwnsAudioFocusNoisyHandlingAndLifecycleRelease() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()

        assertTrue(source.contains("setAudioAttributes(AudioAttributes.DEFAULT, true)"))
        assertTrue(source.contains("setHandleAudioBecomingNoisy(true)"))
        assertTrue(source.contains("session.player.release()"))
        assertTrue(source.contains("session.release()"))
        assertTrue(source.indexOf("session.player.release()") < source.indexOf("session.release()"))
        assertTrue(source.contains("mediaSession = null"))
    }
}
