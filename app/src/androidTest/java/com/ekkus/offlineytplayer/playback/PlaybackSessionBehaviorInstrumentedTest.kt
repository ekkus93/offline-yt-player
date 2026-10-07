package com.ekkus.offlineytplayer.playback

import android.content.ComponentName
import android.os.SystemClock
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
