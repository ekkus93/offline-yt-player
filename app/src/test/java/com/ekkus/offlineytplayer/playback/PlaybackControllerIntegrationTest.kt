package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerIntegrationTest {
    @Test
    fun composePlaybackConnectsThroughMediaControllerNotIndependentExoPlayer() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(source.contains("import androidx.media3.session.MediaController"))
        assertTrue(source.contains("import androidx.media3.session.SessionToken"))
        assertTrue(source.contains("PlaybackSessionService::class.java"))
        assertTrue(source.contains("MediaController.Builder(context, token).buildAsync()"))
        assertFalse(source.contains("import androidx.media3.exoplayer.ExoPlayer"))
        assertFalse(source.contains("ExoPlayer.Builder"))
    }

    @Test
    fun controllerLifecycleIsScopedToComposeAndReleasedSafely() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(source.contains("DisposableEffect("))
        assertTrue(source.contains("future.addListener"))
        assertTrue(source.contains("connected.setMediaItem"))
        assertTrue(source.contains("connected.prepare()"))
        assertTrue(source.contains("controller = connected"))
        assertTrue(source.contains("future.cancel(true)"))
        assertTrue(source.contains("acquiredController?.release()"))
    }

    @Test
    fun uiControlsSendTransportCommandsThroughTheSessionController() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(source.contains("PlayerView(viewContext)"))
        assertTrue(source.contains("player = controller"))
        assertTrue(source.contains("controller?.seekBack()"))
        assertTrue(source.contains("if (it.isPlaying) it.pause() else it.play()"))
        assertTrue(source.contains("controller?.seekForward()"))
        assertTrue(source.contains("controller?.setPlaybackSpeed(speed)"))
        assertTrue(source.contains("Text(if (controller == null) \"Connecting to playback session…\" else \"Offline local playback\")"))
    }
}
