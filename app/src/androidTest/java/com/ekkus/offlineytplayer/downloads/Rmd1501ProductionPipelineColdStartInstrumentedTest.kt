package com.ekkus.offlineytplayer.downloads

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.MainActivity
import com.ekkus.offlineytplayer.MainActivityDependencyOverrides
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.coregateway.DownloadSelectionOptions
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiLibraryPlaybackGateway
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy
import com.ekkus.offlineytplayer.playback.PlaybackSessionService
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import com.ekkus.offlineytplayer.ui.OfflineYTPlayerApp
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
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1501ProductionPipelineColdStartInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val database: File
        get() = File(context.filesDir, DATABASE_NAME)

    // Host-driven phases run in separate instrumentation processes; explicitly initialize
    // the packaged native library before using generated UniFFI gateways.
    @Before
    fun loadNativeCore() {
        System.loadLibrary("offline_yt_core")
    }

    @After
    fun cleanAfter() {
        MainActivityDependencyOverrides.clearForInstrumentation()
        context.stopService(Intent(context, DownloadForegroundService::class.java))
        DownloadForegroundService.connectivityObserverFactoryForTesting = null
        context.stopService(Intent(context, PlaybackSessionService::class.java))
        if (coldStartPhase() != "seed") {
            deleteRuntimeState()
        }
    }

    @Test
    fun seedRunsAnalyzeOptionsSchedulerDownloadAndLibraryPipeline() {
        assumeTrue(coldStartPhase() == "seed")
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        deleteRuntimeState()

        val wave = deterministicWaveBytes()
        val server = OneShotFixtureServer(wave, "audio/wav").also { it.start() }
        val selectedOptions = AtomicReference<DownloadSelectionOptions?>()
        val sourceGateway = FixtureSourceGateway(
            CoreSourceAnalysis(
                sourceUrl = SOURCE_URL,
                title = TITLE,
                durationMs = 5_000,
                thumbnailUrl = null,
                qualityLabel = "Fixture WAV",
                estimatedBytes = wave.size.toLong(),
                qualityOptions = listOf(
                    CoreSourceQualityChoice(
                        label = "Fixture default",
                        estimatedBytes = wave.size.toLong(),
                        choiceId = "fixture-default",
                    ),
                    CoreSourceQualityChoice(
                        label = "Fixture WAV",
                        estimatedBytes = wave.size.toLong(),
                        choiceId = "fixture-wave",
                    ),
                ),
                sourceProvider = "direct-fixture",
                sourceMediaId = "rmd-1501-cold-start",
            ),
        )
        val planningControl = FixturePlanningControlGateway(
            databasePath = database.absolutePath,
            sourceUrl = SOURCE_URL,
            mediaUrl = server.mediaUrl,
            expectedBytes = wave.size,
            selectedOptions = selectedOptions,
        )
        val downloadControl = SchedulingDownloadControlGateway(
            delegate = planningControl,
            scheduler = AndroidDownloadExecutionScheduler(context),
            settingsSnapshot = { AppSettingsSnapshot() },
        )
        // This seed phase qualifies loopback transfer through the real Android
        // scheduler, foreground service and generated core worker. A software
        // emulator may report no eligible default network even when loopback is
        // reachable; isolate that OS-callback prerequisite from the fixture.
        // RMD-1506 requires separate, real OS callback qualification.
        DownloadForegroundService.connectivityObserverFactoryForTesting = { _, onChanged ->
            onChanged(DownloadConnectivity.Unmetered)
            Closeable { }
        }

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { }

        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = SOURCE_URL,
                libraryState = LibraryScreenState.Ready(emptyList()),
                downloadsState = DownloadsScreenState.Ready(emptyList()),
                downloadControlGateway = downloadControl,
                sourceAnalysisGateway = sourceGateway,
                libraryRootPath = context.filesDir.absolutePath,
                settingsSnapshot = AppSettingsSnapshot(),
                onUpdateSettings = {},
            )
        }

        compose.waitUntil(60_000) {
            runCatching { compose.onNodeWithText("Analyze").assertIsEnabled() }.isSuccess

        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(60_000) {
            runCatching { compose.onNodeWithText(TITLE, substring = true).assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Download setup").assertIsDisplayed()
        // This host-driven phase isolates the real scheduler/core/Library/process-death path.
        // Select the fixture quality through the source preference and verify the actual
        // scheduler argument below. ProductionComposeBehaviorTest separately exercises
        // Options -> Select -> Apply; combined options + process-death E2E remains open.
        // The options-return transition is unreliable in the software-emulated host
        // cold-start fixture and must not mask evidence for the remaining runtime steps.
        runCatching {
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithText("Download").assertIsEnabled() }.isSuccess
            }
        }.getOrElse { error ->
            throw AssertionError(
                "Download action did not become enabled on fixture setup. " +
                    "Compose semantics: " + describeSemanticsTree(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()),
                error,
            )
        }
        compose.onNodeWithText("Download").performClick()

        server.joinAndRethrow()

        assertEquals("fixture-wave", selectedOptions.get()?.qualityChoiceId)
        val localFile = File(context.filesDir, MEDIA_RELATIVE_PATH)
        assertTrue(localFile.isFile)
        assertArrayEquals(wave, localFile.readBytes())

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val queue = core.listDownloadQueue()
            assertNull(queue.error)
            val snapshot = requireNotNull(queue.value).single { it.jobId == SOURCE_URL }
            assertEquals(CoreDownloadState.COMPLETED, snapshot.state)
            val library = core.listLibrary()
            assertNull(library.error)
            val item = requireNotNull(library.value).single { it.itemId == SOURCE_URL }
            assertTrue(item.completed)
            assertEquals(TITLE, item.displayTitle)
        }
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptor = requireNotNull(playback.listPlaybackAssets().value).single { it.itemId == SOURCE_URL }
            assertTrue(descriptor.playable)
            assertEquals(MEDIA_RELATIVE_PATH, descriptor.videoRelativePath)
        }
    }

    @Test
    fun verifyAfterHostForceStopPlaysPersistedLocalItemWithoutSourceNetwork() {
        assumeTrue(coldStartPhase() == "verify")
        assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        assertEquals(
            "host qualification must explicitly disable external network before cold-start verification",
            "true",
            InstrumentationRegistry.getArguments().getString("rmdNetworkDisabled"),
        )

        MainActivityDependencyOverrides.clearForInstrumentation()
        val localFile = File(context.filesDir, MEDIA_RELATIVE_PATH)
        assertTrue("downloaded fixture must survive host force-stop", localFile.isFile)

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val library = core.listLibrary()
            assertNull(library.error)
            assertTrue(requireNotNull(library.value).any { it.itemId == SOURCE_URL && it.completed })
        }

        val mediaItem = GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptor = requireNotNull(playback.listPlaybackAssets().value).single { it.itemId == SOURCE_URL }
            assertTrue(descriptor.playable)
            assertEquals(MEDIA_RELATIVE_PATH, descriptor.videoRelativePath)
            LocalPlaybackPolicy.mediaItemFor(
                LocalPlaybackAsset(
                    videoPath = localFile.absolutePath,
                    title = TITLE,
                    itemId = SOURCE_URL,
                ),
            )
        }
        assertEquals(Uri.fromFile(localFile), mediaItem.localConfiguration?.uri)
        assertFalse(requireNotNull(mediaItem.localConfiguration).uri.toString().startsWith("http"))

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithText(TITLE).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Play").performClick()
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithText("Offline local playback").assertIsDisplayed() }.isSuccess
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
            try {
                var readyFromLocalFile = false
                val deadline = SystemClock.elapsedRealtime() + 20_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        readyFromLocalFile =
                            controller.playbackState == Player.STATE_READY &&
                                controller.playWhenReady &&
                                controller.currentMediaItem?.localConfiguration?.uri == Uri.fromFile(localFile)
                    }
                    if (readyFromLocalFile) break
                    SystemClock.sleep(50)
                }
                assertTrue(
                    "cold-started production MediaSession must play the persisted local fixture with source network disabled",
                    readyFromLocalFile,
                )
            } finally {
                instrumentation.runOnMainSync {
                    controller.pause()
                    controller.clearMediaItems()
                    controller.release()
                }
            }
        }
    }

    private fun describeSemanticsTree(node: SemanticsNode, depth: Int = 0): String =
        buildString {
            append("  ".repeat(depth))
            append(node.config)
            append(System.lineSeparator())
            if (depth < 20) {
                node.children.forEach { append(describeSemanticsTree(it, depth + 1)) }
            }
        }

    private fun coldStartPhase(): String? =
        InstrumentationRegistry.getArguments().getString("rmdColdStartPhase")

    private fun deleteRuntimeState() {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name == "items" || file.name.startsWith(DATABASE_NAME)) {
                file.deleteRecursively()
            }
        }
    }

    private fun deterministicWaveBytes(): ByteArray {
        val sampleRate = 8_000
        val seconds = 5
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

    private class FixtureSourceGateway(
        private val analysis: CoreSourceAnalysis,
    ) : AppSourceAnalysisGateway {

        override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> =
            CoreGatewayResult(value = analysis.copy(sourceUrl = sourceUrl), error = null)

        override fun close() = Unit
    }

    private class FixturePlanningControlGateway(
        private val databasePath: String,
        private val sourceUrl: String,
        private val mediaUrl: String,
        private val expectedBytes: Int,
        private val selectedOptions: AtomicReference<DownloadSelectionOptions?>,
    ) : AppDownloadControlGateway {
        override fun enqueue(jobId: String): CoreGatewayResult<Boolean> =
            enqueue(jobId, DownloadSelectionOptions("fixture-default"))

        override fun enqueue(jobId: String, choiceId: String?): CoreGatewayResult<Boolean> =
            enqueue(jobId, DownloadSelectionOptions(choiceId))

        override fun enqueue(jobId: String, options: DownloadSelectionOptions): CoreGatewayResult<Boolean> {
            selectedOptions.set(options)
            seed(jobId)
            return CoreGatewayResult(value = true, error = null)
        }

        override fun pause(jobId: String) = CoreGatewayResult(value = false, error = null)
        override fun resume(jobId: String) = CoreGatewayResult(value = false, error = null)
        override fun cancel(jobId: String) = CoreGatewayResult(value = false, error = null)
        override fun retry(jobId: String) = CoreGatewayResult(value = false, error = null)
        override fun close() = Unit

        private fun seed(jobId: String) {
            val planJson = """
                {
                  "source": {
                    "provider": "direct-fixture",
                    "media_id": "rmd-1501-cold-start",
                    "canonical_url": "$sourceUrl"
                  },
                  "title": "$TITLE",
                  "quality": {
                    "choice_id": "fixture-wave",
                    "label": "Fixture WAV",
                    "estimated_bytes": $expectedBytes,
                    "video_height": null,
                    "audio_only": false,
                    "compatibility": "Preferred"
                  },
                  "assets": [
                    {
                      "asset_id": "combined",
                      "kind": "Video",
                      "url": "$mediaUrl",
                      "relative_path": "$MEDIA_RELATIVE_PATH",
                      "expected_bytes": $expectedBytes,
                      "expected_sha256": null,
                      "mime_type": "audio/wav"
                    }
                  ]
                }
            """.trimIndent()

            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
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
                db.insertOrThrow(
                    "download_jobs",
                    null,
                    ContentValues().apply {
                        put("job_id", jobId)
                        put("state_json", "\"Queued\"")
                        put("bytes_downloaded", 0L)
                        put("total_bytes", expectedBytes.toLong())
                        put("attempt", 0L)
                        putNull("retry_at_epoch_ms")
                        putNull("error_json")
                    },
                )
                db.insertOrThrow(
                    "download_work_items",
                    null,
                    ContentValues().apply {
                        put("job_id", jobId)
                        put("plan_json", planJson)
                        put("created_at_epoch_ms", 1_700_000_000_000L)
                    },
                )
                db.insertOrThrow(
                    "download_presentations",
                    null,
                    ContentValues().apply {
                        put("job_id", jobId)
                        put("display_title", TITLE)
                    },
                )
            }
        }
    }

    private class OneShotFixtureServer(
        private val body: ByteArray,
        private val contentType: String,
    ) {
        private val failure = AtomicReference<Throwable?>()
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = 60_000
        }
        private lateinit var worker: Thread
        val mediaUrl: String = "http://127.0.0.1:" + server.localPort + "/offline.wav"

        fun start() {
            worker = thread(start = true, name = "rmd-1501-cold-start-fixture") {
                try {
                    server.use { socket ->
                        socket.accept().use { client ->
                            val input = client.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                            while (true) {
                                val line = input.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            val headers =
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + contentType + "\r\n" +
                                    "Content-Length: " + body.size + "\r\n" +
                                    "Connection: close\r\n\r\n"
                            client.getOutputStream().use { output ->
                                output.write(headers.toByteArray(StandardCharsets.US_ASCII))
                                output.write(body)
                                output.flush()
                            }
                        }
                    }
                } catch (error: SocketTimeoutException) {
                    failure.set(AssertionError("RMD-1501 fixture server was not contacted", error))
                } catch (error: Throwable) {

                    failure.set(error)
                }
            }
        }

        fun joinAndRethrow() {
            worker.join(60_000)
            assertFalse("fixture server should finish after the production download", worker.isAlive)
            failure.get()?.let { throw AssertionError("fixture server failed", it) }
        }
    }

    private companion object {
        const val DATABASE_NAME = "offline-yt-player.sqlite3"
        const val SOURCE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        const val TITLE = "RMD-1501 production pipeline fixture"
        const val MEDIA_RELATIVE_PATH = "items/rmd-1501-cold-start/offline.wav"
    }
}
