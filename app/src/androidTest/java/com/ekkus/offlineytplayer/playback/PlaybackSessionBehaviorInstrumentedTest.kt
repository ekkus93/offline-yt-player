package com.ekkus.offlineytplayer.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.ui.PortraitPlayerScreen
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSessionBehaviorInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun composeSpeedControlManipulatesTheCanonicalSessionPlayer() {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            compose.setContent {
                PortraitPlayerScreen(
                    asset = LocalPlaybackAsset(
                        videoPath = File(context.cacheDir, "ui-session-fixture.mp4").absolutePath,
                        title = "UI session fixture",
                    ),
                    settings = AppSettingsSnapshot(),
                    onUpdateSettings = {},
                    onBack = {},
                )
            }
            compose.onNodeWithText("Speed 1.0×").performClick()
            waitForControllerState(observer) {
                abs(playbackParameters.speed - 1.25f) < 0.001f
            }
            instrumentation.runOnMainSync {
                assertTrue(abs(observer.playbackParameters.speed - 1.25f) < 0.001f)
            }
        } finally {
            instrumentation.runOnMainSync { observer.release() }
        }
    }

    @Test
    fun independentControllersObserveAndManipulateTheSameServiceOwnedPlayer() {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val first = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        val second = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            val localPath = File(context.cacheDir, "session-state-fixture.mp4").absolutePath
            val item = MediaItem.Builder()
                .setMediaId("session-state-fixture")
                .setUri(android.net.Uri.fromFile(File(localPath)))
                .build()

            instrumentation.runOnMainSync {
                first.setMediaItem(item)
                first.setPlaybackSpeed(1.25f)
                first.play()
            }

            waitForControllerState(second) {
                currentMediaItem?.mediaId == "session-state-fixture" &&
                    abs(playbackParameters.speed - 1.25f) < 0.001f &&
                    playWhenReady
            }

            instrumentation.runOnMainSync {
                second.pause()
                second.setPlaybackSpeed(1.5f)
            }

            waitForControllerState(first) {
                currentMediaItem?.mediaId == "session-state-fixture" &&
                    abs(playbackParameters.speed - 1.5f) < 0.001f &&
                    !playWhenReady
            }

            instrumentation.runOnMainSync {
                assertEquals("session-state-fixture", first.currentMediaItem?.mediaId)
                assertEquals(first.currentMediaItem?.mediaId, second.currentMediaItem?.mediaId)
                assertFalse(first.playWhenReady)
                assertFalse(second.playWhenReady)
                assertTrue(abs(first.playbackParameters.speed - second.playbackParameters.speed) < 0.001f)
            }
        } finally {
            instrumentation.runOnMainSync {
                first.release()
                second.release()
            }
        }
    }


    @Test
    fun composeSeekControlsReachTheCanonicalSessionPosition() {
        val audio = createLocalWaveFixture()
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            compose.setContent {
                PortraitPlayerScreen(
                    asset = LocalPlaybackAsset(videoPath = audio.absolutePath, title = "Seek fixture"),
                    settings = AppSettingsSnapshot(),
                    onUpdateSettings = {},
                    onBack = {},
                )
            }
            waitForControllerState(observer) {
                currentMediaItem?.localConfiguration?.uri?.path == audio.absolutePath &&
                    playbackState == androidx.media3.common.Player.STATE_READY
            }
            compose.waitForIdle()
            compose.onNodeWithText("+10s").performClick()
            waitForControllerState(observer) { currentPosition in 9_000L..12_000L }
            compose.onNodeWithText("-10s").performClick()
            waitForControllerState(observer) { currentPosition in 0L..2_000L }
            instrumentation.runOnMainSync {
                assertEquals(audio.absolutePath, observer.currentMediaItem?.localConfiguration?.uri?.path)
            }
        } finally {
            instrumentation.runOnMainSync { observer.release() }
            audio.delete()
        }
    }


    @Test
    fun systemMediaButtonsPauseResumeAndSeekTheCanonicalSession() {
        val audio = createLocalWaveFixture()
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(audio)))
                controller.prepare()
                controller.play()
            }
            waitForControllerState(controller) {
                playbackState == androidx.media3.common.Player.STATE_READY && playWhenReady
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            fun sendMediaKey(code: Int) {
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            waitForControllerState(controller) { !playWhenReady }
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            waitForControllerState(controller) { playWhenReady }
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            waitForControllerState(controller) { currentPosition >= 9_000L }
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            waitForControllerState(controller) { currentPosition in 0L..3_000L }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
            audio.delete()
        }
    }

    private fun createLocalWaveFixture(): File {
        val sampleRate = 8_000
        val pcmBytes = sampleRate * 45 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmBytes + 36)
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmBytes)
        return File(context.cacheDir, "rmd907-session-seek.wav").apply {
            outputStream().use { output ->
                output.write(header.array())
                val silence = ByteArray(8_192)
                var remaining = pcmBytes
                while (remaining > 0) {
                    val count = minOf(remaining, silence.size)
                    output.write(silence, 0, count)
                    remaining -= count
                }
            }
        }
    }

    private fun waitForControllerState(
        controller: MediaController,
        predicate: MediaController.() -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matches = false
            instrumentation.runOnMainSync { matches = controller.predicate() }
            if (matches) return
            SystemClock.sleep(50)
        }
        instrumentation.runOnMainSync {
            assertTrue(
                "Timed out waiting for shared MediaSession state; item=${controller.currentMediaItem?.mediaId}, " +
                    "speed=${controller.playbackParameters.speed}, playWhenReady=${controller.playWhenReady}",
                controller.predicate(),
            )
        }
    }
}
