package com.ekkus.offlineytplayer.downloads

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
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
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy
import com.ekkus.offlineytplayer.playback.LocalSubtitleTrack
import com.ekkus.offlineytplayer.playback.PlaybackSessionService
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1503SubtitleOfflineInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = File(context.filesDir, DATABASE_NAME)

    @After
    fun cleanRuntimeState() {
        context.stopService(Intent(context, DownloadForegroundService::class.java))
        context.stopService(Intent(context, PlaybackSessionService::class.java))
        if (coldStartPhase() != "seed") {
            deleteRuntimeState()
        }
    }

    @Test
    fun scheduledSubtitlePersistsIdentityAndReachesOfflineCanonicalPlayer() {
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        deleteRuntimeState()

        val jobId = "rmd-1503-subtitle-offline"
        val title = "RMD-1503 offline subtitle fixture"
        val wave = deterministicWaveBytes()
        val subtitle = (
            "WEBVTT\n\n" +
                "00:00.000 --> 00:01.500\n" +
                "Offline subtitle cue\n"
            ).toByteArray(StandardCharsets.UTF_8)
        val server = FixtureHttpServer(
            mapOf(
                "/offline.wav" to FixtureResponse("audio/wav", wave),
                "/captions.vtt" to FixtureResponse("text/vtt", subtitle),
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
            mediaUrl = server.baseUrl + "/offline.wav",
            mediaBytes = wave.size,
            subtitleUrl = server.baseUrl + "/captions.vtt",
            subtitleBytes = subtitle.size,
        )

        val scheduled = AndroidDownloadExecutionScheduler(context).schedule(
            DownloadScheduleRequest(queueItemId = jobId),
        )
        assertEquals(DownloadSchedulerKind.ForegroundServiceFallback, scheduled.kind)
        assertTrue("production scheduler should accept subtitle fixture work", scheduled.accepted)
        waitForState(jobId, CoreDownloadState.COMPLETED)
        server.joinAndRethrow() // No HTTP source remains before playback begins.

        val videoFile = File(context.filesDir, VIDEO_RELATIVE_PATH)
        val subtitleFile = File(context.filesDir, SUBTITLE_RELATIVE_PATH)
        assertTrue(videoFile.isFile)
        assertTrue(subtitleFile.isFile)
        assertTrue(subtitleFile.readText().contains("Offline subtitle cue"))

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val library = core.listLibrary()
            assertNull(library.error)
            val item = requireNotNull(library.value).single { it.itemId == jobId }
            assertTrue(item.completed)
            assertEquals(title, item.displayTitle)
        }

        val asset = GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptors = playback.listPlaybackAssets()
            assertNull(descriptors.error)
            val descriptor = requireNotNull(descriptors.value).single { it.itemId == jobId }
            assertTrue(descriptor.playable)
            assertEquals(VIDEO_RELATIVE_PATH, descriptor.videoRelativePath)
            val persistedSubtitle = descriptor.subtitleTracks.single()
            assertEquals(SUBTITLE_RELATIVE_PATH, persistedSubtitle.relativePath)
            assertEquals("en", persistedSubtitle.language)
            assertEquals("vtt", persistedSubtitle.format)
            assertEquals("human-en", persistedSubtitle.trackId)
            assertEquals("text/vtt", persistedSubtitle.mimeType)

            LocalPlaybackAsset(
                videoPath = videoFile.absolutePath,
                title = title,
                subtitleTracks = listOf(
                    LocalSubtitleTrack(
                        path = subtitleFile.absolutePath,
                        language = persistedSubtitle.language,
                        label = persistedSubtitle.language,
                        mimeType = persistedSubtitle.mimeType,
                    ),
                ),
                itemId = jobId,
            )
        }

        val mediaItem = LocalPlaybackPolicy.mediaItemFor(asset)
        val subtitleConfiguration = requireNotNull(mediaItem.localConfiguration).subtitleConfigurations.single()
        assertEquals(Uri.fromFile(subtitleFile), subtitleConfiguration.uri)
        assertEquals("en", subtitleConfiguration.language)
        assertEquals("text/vtt", subtitleConfiguration.mimeType)
        assertFalse(
            "completed offline media item must not retain a network playback URI",
            requireNotNull(mediaItem.localConfiguration).uri.toString().startsWith("http"),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Play").performClick()
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText("Offline local playback").assertIsDisplayed() }.isSuccess &&
                    runCatching { compose.onNodeWithText("Subtitles: en").assertIsDisplayed() }.isSuccess
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
            val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
            try {
                var selectedEnglishTextTrack = false
                val deadline = SystemClock.elapsedRealtime() + 10_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        selectedEnglishTextTrack = observer.currentTracks.groups.any { group ->
                            group.type == C.TRACK_TYPE_TEXT &&
                                (0 until group.length).any { index ->
                                    group.isTrackSelected(index) &&
                                        group.getTrackFormat(index).language == "en"
                                }
                        }
                    }
                    if (selectedEnglishTextTrack) break
                    SystemClock.sleep(50)
                }
                assertTrue(
                    "production player must select the persisted English subtitle track in the canonical session",
                    selectedEnglishTextTrack,
                )

                compose.onNodeWithText("Subtitles: en").performClick()
                compose.onNodeWithText("Subtitles: Off").assertIsDisplayed()
            } finally {
                instrumentation.runOnMainSync { observer.release() }
            }
        }
    }

    @Test
    fun coldStartSeedPersistsSubtitleAsset() {
        assumeTrue(coldStartPhase() == "seed")
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        deleteRuntimeState()

        val jobId = "rmd-1503-subtitle-offline"
        val title = "RMD-1503 offline subtitle fixture"
        val wave = deterministicWaveBytes()
        val subtitle = (
            "WEBVTT\n\n" +
                "00:00.000 --> 00:01.500\n" +
                "Offline subtitle cue\n"
            ).toByteArray(StandardCharsets.UTF_8)
        val server = FixtureHttpServer(
            mapOf(
                "/offline.wav" to FixtureResponse("audio/wav", wave),
                "/captions.vtt" to FixtureResponse("text/vtt", subtitle),
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
            mediaUrl = server.baseUrl + "/offline.wav",
            mediaBytes = wave.size,
            subtitleUrl = server.baseUrl + "/captions.vtt",
            subtitleBytes = subtitle.size,
        )
        val scheduled = AndroidDownloadExecutionScheduler(context).schedule(
            DownloadScheduleRequest(queueItemId = jobId),
        )
        assertEquals(DownloadSchedulerKind.ForegroundServiceFallback, scheduled.kind)
        assertTrue("production scheduler should accept subtitle cold-start fixture work", scheduled.accepted)
        waitForState(jobId, CoreDownloadState.COMPLETED)
        server.joinAndRethrow()

        assertTrue(File(context.filesDir, VIDEO_RELATIVE_PATH).isFile)
        val subtitleFile = File(context.filesDir, SUBTITLE_RELATIVE_PATH)
        assertTrue(subtitleFile.isFile)
        assertTrue(subtitleFile.readText().contains("Offline subtitle cue"))
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptor = requireNotNull(playback.listPlaybackAssets().value).single { it.itemId == jobId }
            val persistedSubtitle = descriptor.subtitleTracks.single()
            assertEquals(SUBTITLE_RELATIVE_PATH, persistedSubtitle.relativePath)
            assertEquals("en", persistedSubtitle.language)
            assertEquals("vtt", persistedSubtitle.format)
            assertEquals("human-en", persistedSubtitle.trackId)
            assertEquals("text/vtt", persistedSubtitle.mimeType)
        }
    }

    @Test
    fun coldStartVerificationReopensPersistedSubtitleOffline() {
        assumeTrue(coldStartPhase() == "verify")
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        assertEquals(
            "host must disable external network before subtitle cold-start verification",
            "true",
            InstrumentationRegistry.getArguments().getString("rmdNetworkDisabled"),
        )

        val jobId = "rmd-1503-subtitle-offline"
        val title = "RMD-1503 offline subtitle fixture"
        val videoFile = File(context.filesDir, VIDEO_RELATIVE_PATH)
        val subtitleFile = File(context.filesDir, SUBTITLE_RELATIVE_PATH)
        assertTrue("media must survive the host force-stop", videoFile.isFile)
        assertTrue("subtitle must survive the host force-stop", subtitleFile.isFile)

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val library = core.listLibrary()
            assertNull(library.error)
            assertTrue(requireNotNull(library.value).any { it.itemId == jobId && it.completed })
        }
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptor = requireNotNull(playback.listPlaybackAssets().value).single { it.itemId == jobId }
            assertTrue(descriptor.playable)
            val persistedSubtitle = descriptor.subtitleTracks.single()
            assertEquals(SUBTITLE_RELATIVE_PATH, persistedSubtitle.relativePath)
            assertEquals("en", persistedSubtitle.language)
            assertEquals("vtt", persistedSubtitle.format)
            assertEquals("text/vtt", persistedSubtitle.mimeType)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Play").performClick()
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText("Offline local playback").assertIsDisplayed() }.isSuccess &&
                    runCatching { compose.onNodeWithText("Subtitles: en").assertIsDisplayed() }.isSuccess
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
            val observer = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
            try {
                var selectedEnglishTextTrack = false
                val deadline = SystemClock.elapsedRealtime() + 10_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        selectedEnglishTextTrack = observer.currentTracks.groups.any { group ->
                            group.type == C.TRACK_TYPE_TEXT &&
                                (0 until group.length).any { index ->
                                    group.isTrackSelected(index) &&
                                        group.getTrackFormat(index).language == "en"
                                }
                        }
                    }
                    if (selectedEnglishTextTrack) break
                    SystemClock.sleep(50)
                }
                assertTrue(
                    "cold-started production player must select the persisted English subtitle offline",
                    selectedEnglishTextTrack,
                )
            } finally {
                instrumentation.runOnMainSync { observer.release() }
            }
        }
    }

    private fun coldStartPhase(): String? =
        InstrumentationRegistry.getArguments().getString("rmdColdStartPhase")

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
        mediaUrl: String,
        mediaBytes: Int,
        subtitleUrl: String,
        subtitleBytes: Int,
    ) {
        val totalBytes = mediaBytes.toLong() + subtitleBytes.toLong()
        val planJson = """
            {
              "source": {
                "provider": "direct-fixture",
                "media_id": "rmd-1503-subtitle",
                "canonical_url": "$sourceUrl"
              },
              "title": "$title",
              "quality": {
                "choice_id": "fixture-subtitle",
                "label": "Subtitle fixture",
                "estimated_bytes": $totalBytes,
                "video_height": null,
                "audio_only": false,
                "compatibility": "Preferred"
              },
              "assets": [
                {
                  "asset_id": "combined",
                  "kind": "Video",
                  "url": "$mediaUrl",
                  "relative_path": "$VIDEO_RELATIVE_PATH",
                  "expected_bytes": $mediaBytes,
                  "expected_sha256": null,
                  "mime_type": "audio/wav"
                },
                {
                  "asset_id": "subtitle:en:human-en",
                  "kind": "Subtitle",
                  "url": "$subtitleUrl",
                  "relative_path": "$SUBTITLE_RELATIVE_PATH",
                  "expected_bytes": $subtitleBytes,
                  "expected_sha256": null,
                  "mime_type": "text/vtt"
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
        val pcmBytes = sampleRate * 2 * 2
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
            }, "rmd-1503-fixture-http")
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
        const val VIDEO_RELATIVE_PATH = "items/rmd-1503-subtitle-offline/offline.wav"
        const val SUBTITLE_RELATIVE_PATH = "items/rmd-1503-subtitle-offline/captions.vtt"
    }
}
