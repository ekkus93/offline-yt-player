package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionBehaviorQualificationTest {
    @Test
    fun playbackServiceOwnsTheCanonicalSessionPlayer() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()
        assertTrue(service.contains("ExoPlayer.Builder(this).build()"))
        assertTrue(service.contains("MediaSession.Builder(this, player).build()"))
        assertTrue(service.contains("override fun onGetSession"))
        assertTrue(service.contains("session.player.release()"))
        assertTrue(service.contains("session.release()"))
    }

    @Test
    fun composeControlsManipulateTheMediaSessionControllerOnly() {
        val screen = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()
        assertTrue(screen.contains("SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))"))
        assertTrue(screen.contains("MediaController.Builder(context, token).buildAsync()"))
        assertTrue(screen.contains("connected.setMediaItem("))
        assertTrue(screen.contains("controller?.seekBack()"))
        assertTrue(screen.contains("if (it.isPlaying) it.pause() else it.play()"))
        assertTrue(screen.contains("controller?.seekForward()"))
        assertTrue(screen.contains("controller?.setPlaybackSpeed(speed)"))
        assertTrue(screen.contains("onUpdateSettings { playbackSpeed = speed }"))
        assertTrue(screen.contains("acquiredController?.release()"))
    }

    @Test
    fun uiAndSessionShareCurrentItemAndPositionState() {
        val screen = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()
        assertTrue(screen.contains("PlayerView(viewContext).apply"))
        assertTrue(screen.contains("player = controller"))
        assertTrue(screen.contains("update = { it.player = controller }"))
        assertTrue(screen.contains("LocalPlaybackPolicy.mediaItemFor(validated)"))
        assertTrue(screen.contains("LocalPlaybackPolicy.restoredStartPosition(validated.startPositionMs)"))
        assertTrue(screen.contains("activeController.knownPositionMs()"))
        assertTrue(screen.contains("activeController.knownDurationMs()"))
    }

    @Test
    fun headsetAndSystemTransportControlsAreDeclaredForTheSessionPlayer() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()
        assertTrue(service.contains("SupportsLockScreenControls = true"))
        assertTrue(service.contains("SupportsHeadsetControls = true"))
        assertTrue(service.contains("setAudioAttributes(AudioAttributes.DEFAULT, true)"))
        assertTrue(service.contains("setHandleAudioBecomingNoisy(true)"))
    }
}
