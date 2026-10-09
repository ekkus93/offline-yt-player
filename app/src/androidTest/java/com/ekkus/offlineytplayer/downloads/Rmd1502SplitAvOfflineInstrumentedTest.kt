package com.ekkus.offlineytplayer.downloads

import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.MainActivity
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiLibraryPlaybackGateway
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy
import com.ekkus.offlineytplayer.playback.PlaybackSessionService
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1502SplitAvOfflineInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = File(context.filesDir, DATABASE_NAME)

    // Host-driven phases run in separate instrumentation processes; explicitly initialize
    // the packaged native library before using generated UniFFI gateways.
    @Before
    fun loadNativeCore() {
        System.loadLibrary("offline_yt_core")
    }

    @After
    fun cleanRuntimeState() {
        context.stopService(android.content.Intent(context, DownloadForegroundService::class.java))
        context.stopService(android.content.Intent(context, PlaybackSessionService::class.java))
        if (coldStartPhase() != "seed") {
            deleteRuntimeState()
        }
    }

    @Test
    fun scheduledSeparateVideoAndAudioPlayOfflineThroughCanonicalSession() {
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        deleteRuntimeState()

        val jobId = "rmd-1502-split-av"
        val title = "RMD-1502 split A/V fixture"
        val video = Base64.decode(VIDEO_BASE64, Base64.DEFAULT)
        val audio = deterministicWaveBytes()
        val server = FixtureHttpServer(
            mapOf(
                "/video.mp4" to FixtureResponse("video/mp4", video),
                "/audio.wav" to FixtureResponse("audio/wav", audio),
            ),
        ).also { it.start() }

        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { control ->
            val enqueued = control.enqueue(jobId)
            assertNull(enqueued.error)
            assertEquals(true, enqueued.value)
        }
        seedDurableWork(
            jobId = jobId,
            title = title,
            sourceUrl = server.baseUrl + "/fixture-source",
            videoUrl = server.baseUrl + "/video.mp4",
            videoBytes = video.size,
            audioUrl = server.baseUrl + "/audio.wav",
            audioBytes = audio.size,
        )

        val scheduled = AndroidDownloadExecutionScheduler(context).schedule(
            DownloadScheduleRequest(queueItemId = jobId),
        )
        assertEquals(DownloadSchedulerKind.ForegroundServiceFallback, scheduled.kind)
        assertTrue("production scheduler should accept split A/V fixture work", scheduled.accepted)
        waitForState(jobId, CoreDownloadState.COMPLETED)
        server.joinAndRethrow()

        val videoFile = File(context.filesDir, VIDEO_RELATIVE_PATH)
        val audioFile = File(context.filesDir, AUDIO_RELATIVE_PATH)
        assertTrue(videoFile.isFile)
        assertTrue(audioFile.isFile)
        assertEquals(video.size.toLong(), videoFile.length())
        assertEquals(audio.size.toLong(), audioFile.length())

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val library = core.listLibrary()
            assertNull(library.error)
            val item = requireNotNull(library.value).single { it.itemId == jobId }
            assertTrue(item.completed)
            assertEquals(2, item.let { persisted -> persisted.completedAssetsCount() })
        }

        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptors = playback.listPlaybackAssets()
            assertNull(descriptors.error)
            val descriptor = requireNotNull(descriptors.value).single { it.itemId == jobId }
            assertTrue(descriptor.playable)
            assertEquals(VIDEO_RELATIVE_PATH, descriptor.videoRelativePath)
            assertEquals(AUDIO_RELATIVE_PATH, descriptor.audioRelativePath)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Play").performClick()
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText("Offline local playback").assertIsDisplayed() }.isSuccess
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
            val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
            try {
                var readyWithSelectedVideoAndAudio = false
                val deadline = SystemClock.elapsedRealtime() + 10_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        val groups = observer.currentTracks.groups
                        val selectedVideo = groups.any { group ->
                            group.type == C.TRACK_TYPE_VIDEO &&
                                (0 until group.length).any(group::isTrackSelected)
                        }
                        val selectedAudio = groups.any { group ->
                            group.type == C.TRACK_TYPE_AUDIO &&
                                (0 until group.length).any(group::isTrackSelected)
                        }
                        val current = observer.currentMediaItem
                        readyWithSelectedVideoAndAudio =
                            observer.playbackState == Player.STATE_READY &&
                                current != null &&
                                current.localConfiguration?.uri?.path == videoFile.absolutePath &&
                                LocalPlaybackPolicy.splitAudioPathFrom(current) == audioFile.absolutePath &&
                                selectedVideo &&
                                selectedAudio
                    }
                    if (readyWithSelectedVideoAndAudio) break
                    SystemClock.sleep(50)
                }
                assertTrue(
                    "canonical session must merge and select persisted local video and audio tracks offline",
                    readyWithSelectedVideoAndAudio,
                )
            } finally {
                instrumentation.runOnMainSync { observer.release() }
            }
        }
    }


    @Test
    fun coldStartSeedPersistsSeparateVideoAndAudio() {
        assumeTrue(coldStartPhase() == "seed")
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        deleteRuntimeState()

        val jobId = "rmd-1502-split-av"
        val title = "RMD-1502 split A/V fixture"
        val video = Base64.decode(VIDEO_BASE64, Base64.DEFAULT)
        val audio = deterministicWaveBytes()
        val server = FixtureHttpServer(
            mapOf(
                "/video.mp4" to FixtureResponse("video/mp4", video),
                "/audio.wav" to FixtureResponse("audio/wav", audio),
            ),
        ).also { it.start() }

        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { control ->
            val enqueued = control.enqueue(jobId)
            assertNull(enqueued.error)
            assertEquals(true, enqueued.value)
        }
        seedDurableWork(
            jobId = jobId,
            title = title,
            sourceUrl = server.baseUrl + "/fixture-source",
            videoUrl = server.baseUrl + "/video.mp4",
            videoBytes = video.size,
            audioUrl = server.baseUrl + "/audio.wav",
            audioBytes = audio.size,
        )
        // Host cold-start seed must exercise the real foreground scheduler/core worker
        // without requiring the software-emulator default-network callback.
        // RMD-1506 separately qualifies actual OS network transition callbacks.
        DownloadForegroundService.connectivityObserverFactoryForTesting = { _, onChanged ->
            onChanged(DownloadConnectivity.Unmetered)
            Closeable { }
        }
        val scheduled = AndroidDownloadExecutionScheduler(context).schedule(
            DownloadScheduleRequest(queueItemId = jobId),
        )
        assertEquals(DownloadSchedulerKind.ForegroundServiceFallback, scheduled.kind)
        assertTrue("production scheduler should accept split A/V cold-start fixture work", scheduled.accepted)
        waitForState(jobId, CoreDownloadState.COMPLETED)
        server.joinAndRethrow()

        val videoFile = File(context.filesDir, VIDEO_RELATIVE_PATH)
        val audioFile = File(context.filesDir, AUDIO_RELATIVE_PATH)
        assertTrue(videoFile.isFile)
        assertTrue(audioFile.isFile)
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptor = requireNotNull(playback.listPlaybackAssets().value).single { it.itemId == jobId }
            assertTrue(descriptor.playable)
            assertEquals(VIDEO_RELATIVE_PATH, descriptor.videoRelativePath)
            assertEquals(AUDIO_RELATIVE_PATH, descriptor.audioRelativePath)
        }
    }

    @Test
    fun coldStartVerificationReopensPersistedSplitAvOffline() {
        assumeTrue(coldStartPhase() == "verify")
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        assertEquals(
            "host must disable external network before split A/V cold-start verification",
            "true",
            InstrumentationRegistry.getArguments().getString("rmdNetworkDisabled"),
        )

        val jobId = "rmd-1502-split-av"
        val title = "RMD-1502 split A/V fixture"
        val videoFile = File(context.filesDir, VIDEO_RELATIVE_PATH)
        val audioFile = File(context.filesDir, AUDIO_RELATIVE_PATH)
        assertTrue("video must survive the host force-stop", videoFile.isFile)
        assertTrue("audio must survive the host force-stop", audioFile.isFile)

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val library = core.listLibrary()
            assertNull(library.error)
            assertTrue(requireNotNull(library.value).any { it.itemId == jobId && it.completed })
        }
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptor = requireNotNull(playback.listPlaybackAssets().value).single { it.itemId == jobId }
            assertTrue(descriptor.playable)
            assertEquals(VIDEO_RELATIVE_PATH, descriptor.videoRelativePath)
            assertEquals(AUDIO_RELATIVE_PATH, descriptor.audioRelativePath)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Play").performClick()
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText("Offline local playback").assertIsDisplayed() }.isSuccess
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
            val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
            try {
                // The video-only fixture is short. It can reach STATE_ENDED before
                // this independent observer attaches. Seek to its start and pause
                // the canonical session so the decoder track proof is deterministic.
                val attachDeadline = SystemClock.elapsedRealtime() + 10_000L
                var correctItemAttached = false
                while (SystemClock.elapsedRealtime() < attachDeadline) {
                    instrumentation.runOnMainSync {
                        correctItemAttached =
                            observer.currentMediaItem?.localConfiguration?.uri?.path == videoFile.absolutePath &&
                                observer.currentMediaItem?.let(LocalPlaybackPolicy::splitAudioPathFrom) ==
                                    audioFile.absolutePath
                    }
                    if (correctItemAttached) break
                    SystemClock.sleep(50)
                }
                assertTrue("cold-started canonical session must reopen both local asset paths", correctItemAttached)
                instrumentation.runOnMainSync {
                    observer.pause()
                    observer.seekTo(0L)
                    observer.prepare()
                }
                var readyWithSelectedVideoAndAudio = false
                var diagnostics = "No session state observed"
                val deadline = SystemClock.elapsedRealtime() + 15_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        val groups = observer.currentTracks.groups
                        val selectedVideo = groups.any { group ->
                            group.type == C.TRACK_TYPE_VIDEO &&
                                (0 until group.length).any(group::isTrackSelected)
                        }
                        val selectedAudio = groups.any { group ->
                            group.type == C.TRACK_TYPE_AUDIO &&
                                (0 until group.length).any(group::isTrackSelected)
                        }
                        val current = observer.currentMediaItem
                        diagnostics = "state=${observer.playbackState}, " +
                            "error=${observer.playerError}, selectedVideo=$selectedVideo, " +
                            "selectedAudio=$selectedAudio, position=${observer.currentPosition}, " +
                            "media=${current?.localConfiguration?.uri}"
                        readyWithSelectedVideoAndAudio =
                            observer.playbackState == Player.STATE_READY &&
                                current?.localConfiguration?.uri?.path == videoFile.absolutePath &&
                                LocalPlaybackPolicy.splitAudioPathFrom(current) == audioFile.absolutePath &&
                                selectedVideo && selectedAudio
                    }
                    if (readyWithSelectedVideoAndAudio) break
                    SystemClock.sleep(50)
                }
                assertTrue(
                    "cold-started canonical session must merge persisted local video and audio offline; $diagnostics",
                    readyWithSelectedVideoAndAudio,
                )
            } finally {
                instrumentation.runOnMainSync { observer.release() }
            }
        }
    }

    private fun coldStartPhase(): String? =
        InstrumentationRegistry.getArguments().getString("rmdColdStartPhase")

    private fun com.ekkus.offlineytplayer.coregateway.CoreLibraryItem.completedAssetsCount(): Int =
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptor = requireNotNull(playback.listPlaybackAssets().value).single { it.itemId == itemId }
            listOfNotNull(descriptor.videoRelativePath, descriptor.audioRelativePath).size
        }

    private fun waitForState(jobId: String, expected: CoreDownloadState) {
        var state: CoreDownloadState? = null
        val deadline = SystemClock.elapsedRealtime() + 60_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val queue = core.listDownloadQueue()
                assertNull(queue.error)
                state = queue.value?.firstOrNull { it.jobId == jobId }?.state
            }
            if (state == expected || state == CoreDownloadState.FAILED) break
            SystemClock.sleep(250)
        }
        assertEquals(expected, state)
    }

    private fun seedDurableWork(
        jobId: String,
        title: String,
        sourceUrl: String,
        videoUrl: String,
        videoBytes: Int,
        audioUrl: String,
        audioBytes: Int,
    ) {
        val totalBytes = videoBytes.toLong() + audioBytes.toLong()
        val planJson = """
            {
              "source": {
                "provider": "direct-fixture",
                "media_id": "rmd-1502-split-av",
                "canonical_url": "$sourceUrl"
              },
              "title": "$title",
              "quality": {
                "choice_id": "fixture-split-av",
                "label": "Split A/V fixture",
                "estimated_bytes": $totalBytes,
                "video_height": 16,
                "audio_only": false,
                "compatibility": "RequiresSeparateAssets"
              },
              "assets": [
                {
                  "asset_id": "video",
                  "kind": "Video",
                  "url": "$videoUrl",
                  "relative_path": "$VIDEO_RELATIVE_PATH",
                  "expected_bytes": $videoBytes,
                  "expected_sha256": null,
                  "mime_type": "video/mp4"
                },
                {
                  "asset_id": "audio",
                  "kind": "Audio",
                  "url": "$audioUrl",
                  "relative_path": "$AUDIO_RELATIVE_PATH",
                  "expected_bytes": $audioBytes,
                  "expected_sha256": null,
                  "mime_type": "audio/wav"
                }
              ]
            }
        """.trimIndent()

        SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_work_items (
                  job_id TEXT PRIMARY KEY,
                  plan_json TEXT NOT NULL,
                  created_at_epoch_ms INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_presentations (
                  job_id TEXT PRIMARY KEY,
                  display_title TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT OR REPLACE INTO download_work_items(job_id, plan_json, created_at_epoch_ms) VALUES(?, ?, ?)",
                arrayOf<Any>(jobId, planJson, 1_700_000_000_000L),
            )
            db.execSQL(
                "INSERT OR REPLACE INTO download_presentations(job_id, display_title) VALUES(?, ?)",
                arrayOf<Any>(jobId, title),
            )
        }
    }

    private fun deterministicWaveBytes(): ByteArray {
        val sampleRate = 8_000
        val seconds = 4
        val pcmBytes = sampleRate * 2 * seconds
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        header.putInt(pcmBytes + 36)
        header.put("WAVEfmt ".toByteArray(StandardCharsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(StandardCharsets.US_ASCII))
        header.putInt(pcmBytes)
        return header.array() + ByteArray(pcmBytes)
    }

    private fun deleteRuntimeState() {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name == "items" || file.name.startsWith(DATABASE_NAME)) {
                file.deleteRecursively()
            }
        }
    }

    private data class FixtureResponse(val contentType: String, val body: ByteArray)

    private class FixtureHttpServer(private val responses: Map<String, FixtureResponse>) {
        private val failure = AtomicReference<Throwable?>()
        private val server = ServerSocket(0, responses.size, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = 20_000
        }
        private lateinit var thread: Thread
        val baseUrl: String = "http://127.0.0.1:" + server.localPort

        fun start() {
            thread = Thread({
                try {
                    server.use { socket ->
                        repeat(responses.size) {
                            socket.accept().use { client ->
                                val input = client.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                                val requestLine = input.readLine() ?: error("fixture request line was absent")
                                while (true) {
                                    val line = input.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                                val path = requestLine.split(' ').getOrNull(1)
                                    ?: error("fixture request path was absent")
                                val response = responses[path]
                                    ?: error("unexpected fixture request path: $path")
                                val header = (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: " + response.contentType + "\r\n" +
                                        "Content-Length: " + response.body.size + "\r\n" +
                                        "Connection: close\r\n\r\n"
                                    )
                                client.getOutputStream().use { output ->
                                    output.write(header.toByteArray(StandardCharsets.US_ASCII))
                                    output.write(response.body)
                                    output.flush()
                                }
                            }
                        }
                    }
                } catch (error: SocketTimeoutException) {
                    failure.set(AssertionError("fixture server did not receive all expected requests", error))
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }, "rmd-1502-fixture-http")
            thread.start()
        }

        fun joinAndRethrow() {
            thread.join(20_000)
            assertTrue("fixture server thread should finish", !thread.isAlive)
            failure.get()?.let { throw AssertionError("fixture server failed", it) }
        }
    }

    private companion object {
        const val DATABASE_NAME = "offline-yt-player.sqlite3"
        const val VIDEO_RELATIVE_PATH = "items/rmd-1502-split-av/video.mp4"
        const val AUDIO_RELATIVE_PATH = "items/rmd-1502-split-av/audio.wav"
        const val VIDEO_BASE64 = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAANAbW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAD6AAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAmt0cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAD6AAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAABAAAAAQAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAA+gAAAAAAABAAAAAAHjbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAABAAAABAABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABjm1pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAAU5zdGJsAAAAtnN0c2QAAAAAAAAAAQAAAKZhdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAABAAEABIAAAASAAAAAAAAAABFUxhdmM2MS4xOS4xMDEgbGlieDI2NAAAAAAAAAAAAAAAGP//AAAALGF2Y0MBQsAK/+EAFWdCwAraewEQAAADABAAAAMASPEiagEABGjOD8gAAAAQcGFzcAAAAAEAAAABAAAAFGJ0cnQAAAAAAAAFWgAAAAAAAAAYc3R0cwAAAAAAAAABAAAACAAAIAAAAAAUc3RzcwAAAAAAAAABAAAAAQAAABxzdHNjAAAAAAAAAAEAAAABAAAACAAAAAEAAAA0c3RzegAAAAAAAAAAAAAACAAAAm4AAAAJAAAACQAAAAkAAAAJAAAACQAAAAkAAAAJAAAAFHN0Y28AAAAAAAAAAQAAA3AAAABhdWR0YQAAAFltZXRhAAAAAAAAACFoZGxyAAAAAAAAAABtZGlyYXBwbAAAAAAAAAAAAAAAACxpbHN0AAAAJKl0b28AAAAcZGF0YQAAAAEAAAAATGF2ZjYxLjcuMTAzAAAACGZyZWUAAAK1bWRhdAAAAlMGBf//T9xF6b3m2Ui3lizYINkj7u94MjY0IC0gY29yZSAxNjQgcjMxMDggMzFlMTlmOSAtIEguMjY0L01QRUctNCBBVkMgY29kZWMgLSBDb3B5bGVmdCAyMDAzLTIwMjMgLSBodHRwOi8vd3d3LnZpZGVvbGFuLm9yZy94MjY0Lmh0bWwgLSBvcHRpb25zOiBjYWJhYz0wIHJlZj0xIGRlYmxvY2s9MDowOjAgYW5hbHlzZT0wOjAgbWU9ZGlhIHN1Ym1lPTAgcHN5PTEgcHN5X3JkPTEuMDA6MC4wMCBtaXhlZF9yZWY9MCBtZV9yYW5nZT0xNiBjaHJvbWFfbWU9MSB0cmVsbGlzPTAgOHg4ZGN0PTAgY3FtPTAgZGVhZHpvbmU9MjEsMTEgZmFzdF9wc2tpcD0xIGNocm9tYV9xcF9vZmZzZXQ9MCB0aHJlYWRzPTEgbG9va2FoZWFkX3RocmVhZHM9MSBzbGljZWRfdGhyZWFkcz0wIG5yPTAgZGVjaW1hdGU9MSBpbnRlcmxhY2VkPTAgYmx1cmF5X2NvbXBhdD0wIGNvbnN0cmFpbmVkX2ludHJhPTAgYmZyYW1lcz0wIHdlaWdodHA9MCBrZXlpbnQ9MjUwIGtleWludF9taW49MiBzY2VuZWN1dD0wIGludHJhX3JlZnJlc2g9MCByYz1jcmYgbWJ0cmVlPTAgY3JmPTIzLjAgcWNvbXA9MC42MCBxcG1pbj0wIHFwbWF4PTY5IHFwc3RlcD00IGlwX3JhdGlvPTEuNDAgYXE9MACAAAAAE2WIhDoRigACMXHAAEPKOAAIBeAAAAAFQZogEqUAAAAFQZpAE6UAAAAFQZpgE6UAAAAFQZqAE6UAAAAFQZqgE6UAAAAFQZrAE6UAAAAFQZrgE6U="
    }
}
