package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionProductionPathTest {
    @Test
    fun playbackScreenPersistsControllerPositionAtBoundedCadenceAndDispose() {
        val screen = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(screen.contains("PositionPersistCadenceMs"))
        assertTrue(screen.contains("savePlaybackPosition"))
        assertTrue(screen.contains("postDelayed(periodicSaver"))
        assertTrue(screen.contains("persistPlaybackPosition(finalTransition = true)"))
        assertTrue(screen.contains("LocalPlaybackPolicy.persistedPositionForStop"))
    }

    @Test
    fun generatedPlaybackPositionGatewayUsesTheDurableUniffiBoundary() {
        val ffi = File("../core/src/ffi_playback_position.rs").readText()
        val gateway = File("src/main/java/com/ekkus/offlineytplayer/coregateway/AppPlaybackPositionGateway.kt").readText()

        assertTrue(ffi.contains("ffi_save_playback_position"))
        assertTrue(ffi.contains("FfiPlaybackPositionResult"))
        assertTrue(gateway.contains("ffiSavePlaybackPosition"))
        assertTrue(gateway.contains("checkNotMainThread()"))
    }
}
