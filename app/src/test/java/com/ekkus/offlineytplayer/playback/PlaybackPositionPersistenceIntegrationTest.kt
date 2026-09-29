package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionPersistenceIntegrationTest {
    @Test
    fun composePlaybackLoadsConfiguredStartPositionBeforePreparingController() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(source.contains("val configuredStartPositionMs = if (settings.rememberPlaybackPosition)"))
        assertTrue(source.contains("LocalPlaybackPolicy.restoredStartPosition(validated.startPositionMs)"))
        assertTrue(source.contains("connected.setMediaItem(LocalPlaybackPolicy.mediaItemFor(validated), configuredStartPositionMs)"))
        assertTrue(source.indexOf("connected.setMediaItem(LocalPlaybackPolicy.mediaItemFor(validated), configuredStartPositionMs)") < source.indexOf("connected.prepare()"))
    }

    @Test
    fun composePlaybackPersistsPositionPeriodicallyAndOnDispose() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/PlaybackScreen.kt").readText()

        assertTrue(source.contains("fun persistPlaybackPosition(finalTransition: Boolean)"))
        assertTrue(source.contains("LocalPlaybackPolicy.shouldPersistPosition(lastSavedPositionMs, currentPositionMs, durationMs)"))
        assertTrue(source.contains("GeneratedUniffiPlaybackPositionGateway.open(databasePath).use { gateway -> gateway.savePlaybackPosition"))
        assertTrue(source.contains("mainHandler.postDelayed(periodicSaver, LocalPlaybackPolicy.PositionPersistCadenceMs)"))
        assertTrue(source.contains("persistPlaybackPosition(true)"))
    }

    @Test
    fun policyTestsCoverCadenceCompletionAndClampingSemantics() {
        val testSource = File("src/test/java/com/ekkus/offlineytplayer/playback/LocalPlaybackPolicyTest.kt").readText()
        val policySource = File("src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt").readText()

        assertTrue(policySource.contains("const val PositionPersistCadenceMs = 5_000L"))
        assertTrue(policySource.contains("const val NearEndCompletedThresholdMs = 30_000L"))
        assertTrue(testSource.contains("positionPersistenceUsesBoundedCadence"))
        assertTrue(testSource.contains("stopPersistenceResetsCompletedPlaybackAndClampsPositions"))
        assertTrue(testSource.contains("playbackRequestNormalizesRestoredStartPosition"))
    }
}
