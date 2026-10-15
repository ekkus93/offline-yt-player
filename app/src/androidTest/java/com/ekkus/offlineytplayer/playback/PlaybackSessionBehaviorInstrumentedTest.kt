package com.ekkus.offlineytplayer.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.AppPlaybackPositionGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.ui.PortraitPlayerScreen
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
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
    fun media3ItemSanitizesProviderTitleAndSubtitleLabelsBeforeSessionExposure() {
        val secret = "RMD1802_MEDIA3_PRIVATE_LABEL"
        val item = LocalPlaybackPolicy.mediaItemFor(
            LocalPlaybackAsset(
                videoPath = File(context.cacheDir, "metadata-fixture.mp4").absolutePath,
                title = "Video https://example.test/title?token=$secret",
                subtitleTracks = listOf(
                    LocalSubtitleTrack(
                        path = File(context.cacheDir, "metadata-fixture.vtt").absolutePath,
                        language = "en",
                        label = "English https://example.test/sub?token=$secret",
                        mimeType = androidx.media3.common.MimeTypes.TEXT_VTT,
                    ),
                ),
            ),
        )
        assertEquals(
            "Video https://example.test/title?REDACTED",
            item.mediaMetadata.title.toString(),
        )
        val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty()
        assertEquals(
            "English https://example.test/sub?REDACTED",
            subtitles.single().label,
        )
        assertTrue(secret !in item.mediaMetadata.title.toString())
        assertTrue(secret !in subtitles.single().label.orEmpty())
    }

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
            compose.onNodeWithText("Seek fixture").assertIsDisplayed()
            instrumentation.runOnMainSync { assertEquals("Seek fixture", observer.currentMediaItem?.mediaMetadata?.title?.toString()) }
            compose.onNodeWithText("+10s").performClick()
            waitForControllerState(observer) { currentPosition in 9_000L..12_000L }
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Position 0:10").assertIsDisplayed() }.isSuccess }
            compose.onNodeWithText("-10s").performClick()
            waitForControllerState(observer) { currentPosition in 0L..2_000L }
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Position 0:00").assertIsDisplayed() }.isSuccess }
            instrumentation.runOnMainSync { observer.seekTo(20_000L) }
            waitForControllerState(observer) { currentPosition in 19_000L..21_000L }
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Position 0:20").assertIsDisplayed() }.isSuccess }
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

    @Test
    fun sessionPlayerDeclaresMediaAudioUsage() {
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
            // setHandleAudioBecomingNoisy(true) is not asserted here: ACTION_AUDIO_BECOMING_NOISY is a
            // protected system broadcast that an app or shell cannot send, so that setter stays unverified
            // (see docs/RMD_1802_SOURCE_TEXT_TEST_AUDIT_2026-10-15.md).
            instrumentation.runOnMainSync { assertEquals(C.USAGE_MEDIA, controller.audioAttributes.usage) }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
            audio.delete()
        }
    }

    @Test
    fun splitAudioPathSurvivesTheMediaIdTransportedByControllersAndRejectsRemoteIds() {
        val audioPath = File(context.cacheDir, "items/a b/audio 128k.m4a").absolutePath
        val item = LocalPlaybackPolicy.mediaItemFor(
            LocalPlaybackAsset(
                videoPath = File(context.cacheDir, "items/a b/video.mp4").absolutePath,
                audioPath = audioPath,
                title = "Split fixture",
            ),
        )
        // MediaSession controllers serialize the media ID, not LocalConfiguration.tag.
        val transported = MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setUri(item.localConfiguration?.uri)
            .build()
        assertEquals(audioPath, LocalPlaybackPolicy.splitAudioPathFrom(transported))
        assertEquals(null, LocalPlaybackPolicy.splitAudioPathFrom(LocalPlaybackPolicy.mediaItemFor("/library/video.mp4")))

        val forged = MediaItem.Builder()
            .setMediaId("offline-yt-player/split-audio/" + android.net.Uri.encode("https://example.invalid/a.m4a"))
            .setUri("file:///library/video.mp4")
            .build()
        val rejected = runCatching { LocalPlaybackPolicy.splitAudioPathFrom(forged) }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)
    }

    @Test
    fun subtitleControlCyclesTracksThenOffOnTheSessionTrackSelection() {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            compose.setContent {
                PortraitPlayerScreen(
                    asset = LocalPlaybackAsset(
                        videoPath = File(context.cacheDir, "subtitle-select.mp4").absolutePath,
                        title = "Subtitle select fixture",
                        subtitleTracks = listOf(
                            LocalSubtitleTrack(File(context.cacheDir, "en.vtt").absolutePath, "en", "English", MimeTypes.TEXT_VTT),
                            LocalSubtitleTrack(File(context.cacheDir, "fr.vtt").absolutePath, "fr", "French", MimeTypes.TEXT_VTT),
                        ),
                    ),
                    settings = AppSettingsSnapshot(),
                    onUpdateSettings = {},
                    onBack = {},
                    positionGatewayFactory = { RecordingPositionGateway() },
                )
            }
            waitForControllerState(observer) {
                trackSelectionParameters.preferredTextLanguages == listOf("en") &&
                    C.TRACK_TYPE_TEXT !in trackSelectionParameters.disabledTrackTypes
            }
            compose.onNodeWithText("Subtitles: English").performClick()
            compose.onNodeWithText("Subtitles: French").assertIsDisplayed()
            waitForControllerState(observer) { trackSelectionParameters.preferredTextLanguages == listOf("fr") }

            compose.onNodeWithText("Subtitles: French").performClick()
            compose.onNodeWithText("Subtitles: Off").assertIsDisplayed()
            waitForControllerState(observer) { C.TRACK_TYPE_TEXT in trackSelectionParameters.disabledTrackTypes }

            compose.onNodeWithText("Subtitles: Off").performClick()
            compose.onNodeWithText("Subtitles: English").assertIsDisplayed()
            waitForControllerState(observer) {
                C.TRACK_TYPE_TEXT !in trackSelectionParameters.disabledTrackTypes &&
                    trackSelectionParameters.preferredTextLanguages == listOf("en")
            }
        } finally {
            instrumentation.runOnMainSync { observer.release() }
        }
    }

    @Test
    fun audioControlCyclesPersistedTracksAndWrapsOnTheSessionTrackSelection() {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            compose.setContent {
                PortraitPlayerScreen(
                    asset = LocalPlaybackAsset(
                        videoPath = File(context.cacheDir, "audio-select.mp4").absolutePath,
                        title = "Audio select fixture",
                        audioTracks = listOf(
                            LocalAudioTrack(File(context.cacheDir, "en.m4a").absolutePath, "en", "English"),
                            LocalAudioTrack(File(context.cacheDir, "fr.m4a").absolutePath, "fr", "French"),
                        ),
                    ),
                    settings = AppSettingsSnapshot(),
                    onUpdateSettings = {},
                    onBack = {},
                    positionGatewayFactory = { RecordingPositionGateway() },
                )
            }
            compose.onNodeWithText("Audio: English").performClick()
            compose.onNodeWithText("Audio: French").assertIsDisplayed()
            waitForControllerState(observer) { trackSelectionParameters.preferredAudioLanguages == listOf("fr") }

            compose.onNodeWithText("Audio: French").performClick()
            compose.onNodeWithText("Audio: English").assertIsDisplayed()
            waitForControllerState(observer) { trackSelectionParameters.preferredAudioLanguages == listOf("en") }
        } finally {
            instrumentation.runOnMainSync { observer.release() }
        }
    }

    @Test
    fun defaultPlaybackSpeedSettingIsAppliedToTheSessionPlayer() {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            compose.setContent {
                PortraitPlayerScreen(
                    asset = LocalPlaybackAsset(
                        videoPath = File(context.cacheDir, "speed-setting.mp4").absolutePath,
                        title = "Speed setting fixture",
                    ),
                    settings = AppSettingsSnapshot(playbackSpeed = 1.5f),
                    onUpdateSettings = {},
                    onBack = {},
                    positionGatewayFactory = { RecordingPositionGateway() },
                )
            }
            waitForControllerState(observer) { abs(playbackParameters.speed - 1.5f) < 0.001f }
        } finally {
            instrumentation.runOnMainSync { observer.release() }
        }
    }

    @Test
    fun speedControlEmitsTheNextDefaultSpeedSettingMutation() {
        val emitted = CopyOnWriteArrayList<Float>()
        compose.setContent {
            PortraitPlayerScreen(
                asset = LocalPlaybackAsset(
                    videoPath = File(context.cacheDir, "speed-mutation.mp4").absolutePath,
                    title = "Speed mutation fixture",
                ),
                settings = AppSettingsSnapshot(playbackSpeed = 1.0f),
                onUpdateSettings = { mutation ->
                    emitted += com.ekkus.offlineytplayer.settings.AppSettingsMutation(AppSettingsSnapshot(playbackSpeed = 1.0f))
                        .apply(mutation).build().playbackSpeed
                },
                positionGatewayFactory = { RecordingPositionGateway() },
                onBack = {},
            )
        }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Offline local playback").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Speed 1.0×").performClick()
        compose.runOnIdle { assertEquals(listOf(1.25f), emitted.toList()) }
    }

    @Test
    fun enabledResumeStartsAtStoredPositionAndDisposalPersistsTheControllerPosition() {
        val audio = createLocalWaveFixture()
        val saves = RecordingPositionGateway()
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        val showPlayer = mutableStateOf(true)
        try {
            compose.setContent {
                if (showPlayer.value) {
                    PortraitPlayerScreen(
                        asset = LocalPlaybackAsset(
                            videoPath = audio.absolutePath,
                            title = "Resume fixture",
                            startPositionMs = 15_000L,
                            itemId = "library-item-1",
                        ),
                        settings = AppSettingsSnapshot(rememberPlaybackPosition = true),
                        onUpdateSettings = {},
                        onBack = {},
                        positionGatewayFactory = { saves },
                    )
                }
            }
            waitForControllerState(observer) {
                playbackState == androidx.media3.common.Player.STATE_READY && currentPosition in 14_000L..20_000L
            }
            instrumentation.runOnMainSync { observer.seekTo(30_000L) }
            waitForControllerState(observer) { currentPosition in 29_000L..33_000L }

            compose.runOnIdle { showPlayer.value = false }
            compose.waitForIdle()

            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (saves.calls.none { it.positionMs in 29_000L..34_000L } && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
            }
            val final = saves.calls.last { it.positionMs in 29_000L..34_000L }
            assertEquals("library-item-1", final.itemId)
            assertTrue("duration should be reported, was ${final.durationMs}", (final.durationMs ?: -1L) in 44_000L..46_000L)
        } finally {
            instrumentation.runOnMainSync { observer.release() }
            audio.delete()
        }
    }

    @Test
    fun disabledResumeStartsAtZeroAndNeverWritesPosition() {
        val audio = createLocalWaveFixture()
        val saves = RecordingPositionGateway()
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        val showPlayer = mutableStateOf(true)
        try {
            compose.setContent {
                if (showPlayer.value) {
                    PortraitPlayerScreen(
                        asset = LocalPlaybackAsset(
                            videoPath = audio.absolutePath,
                            title = "No-resume fixture",
                            startPositionMs = 15_000L,
                            itemId = "library-item-2",
                        ),
                        settings = AppSettingsSnapshot(rememberPlaybackPosition = false),
                        onUpdateSettings = {},
                        onBack = {},
                        positionGatewayFactory = { saves },
                    )
                }
            }
            waitForControllerState(observer) {
                playbackState == androidx.media3.common.Player.STATE_READY && currentPosition in 0L..8_000L
            }
            compose.runOnIdle { showPlayer.value = false }
            compose.waitForIdle()
            SystemClock.sleep(750)

            assertTrue("no position may be written when resume is disabled: ${saves.calls}", saves.calls.isEmpty())
        } finally {
            instrumentation.runOnMainSync { observer.release() }
            audio.delete()
        }
    }

    private class RecordingPositionGateway : AppPlaybackPositionGateway {
        data class Call(val itemId: String, val positionMs: Long, val durationMs: Long?)

        val calls = CopyOnWriteArrayList<Call>()

        override fun savePlaybackPosition(itemId: String, positionMs: Long, durationMs: Long?): CoreGatewayResult<Boolean> {
            calls.add(Call(itemId, positionMs, durationMs))
            return CoreGatewayResult(value = true, error = null)
        }

        override fun close() = Unit
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
                    "uri=${controller.currentMediaItem?.localConfiguration?.uri}, " +
                    "requestUri=${controller.currentMediaItem?.requestMetadata?.mediaUri}, " +
                    "state=${controller.playbackState}, position=${controller.currentPosition}, " +
                    "speed=${controller.playbackParameters.speed}, playWhenReady=${controller.playWhenReady}",
                controller.predicate(),
            )
        }
    }
}
