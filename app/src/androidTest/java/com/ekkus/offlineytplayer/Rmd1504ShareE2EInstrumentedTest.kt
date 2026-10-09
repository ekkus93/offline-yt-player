package com.ekkus.offlineytplayer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.os.SystemClock
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.DownloadSelectionOptions
import com.ekkus.offlineytplayer.downloads.DownloadConnectivity
import com.ekkus.offlineytplayer.downloads.DownloadForegroundService
import com.ekkus.offlineytplayer.downloads.DownloadServicePolicy
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1504ShareE2EInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun cleanBefore() {
        MainActivityDependencyOverrides.clearForInstrumentation()
        stopFixtureService()
        deleteRuntimeState()
    }

    @After
    fun cleanAfter() {
        MainActivityDependencyOverrides.clearForInstrumentation()
        stopFixtureService()
        deleteRuntimeState()
    }

    @Test
    fun actionSendFlowsThroughAnalyzeSetupRealSchedulerWorkerLibraryAndBackStack() {
        val sharedUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val title = "RMD-1504 shared fixture"
        val payload = "rmd-1504 deterministic downloaded bytes".toByteArray(StandardCharsets.UTF_8)
        val server = OneShotFixtureServer(payload).also { it.start() }
        // Qualify ACTION_SEND -> real scheduler/core worker independently of
        // an emulator's default-network callback, which can report no network
        // even though the loopback fixture is reachable. OS network transitions
        // remain qualified separately under RMD-1506.
        DownloadForegroundService.connectivityObserverFactoryForTesting = { _, onChanged ->
            onChanged(DownloadConnectivity.Unmetered)
            Closeable { }
        }

        MainActivityDependencyOverrides.installForInstrumentation(
            sourceFactory = {
                FixtureSourceGateway(
                    CoreSourceAnalysis(
                        sourceUrl = sharedUrl,
                        title = title,
                        durationMs = 4_000,
                        thumbnailUrl = null,
                        qualityLabel = "Fixture 720p",
                        estimatedBytes = payload.size.toLong(),
                        qualityOptions = listOf(
                            CoreSourceQualityChoice(
                                label = "Fixture 720p",
                                estimatedBytes = payload.size.toLong(),
                                choiceId = "fixture-720p",
                            ),
                        ),
                        sourceProvider = "direct-fixture",
                        sourceMediaId = "rmd-1504-share",
                    ),
                )
            },
            controlsFactory = { databasePath ->
                FixturePlanningControlGateway(
                    databasePath = databasePath,
                    sourceUrl = sharedUrl,
                    title = title,
                    mediaUrl = server.mediaUrl,
                    expectedBytes = payload.size,
                )
            },
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Shared from another app: $sharedUrl")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithText("Analyze").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithText("Analyze").performClick()
            compose.waitUntil(20_000) {
                runCatching { compose.onNodeWithText(title, substring = true).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Download setup").assertIsDisplayed()
            compose.onNodeWithText("Download").performClick()
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNodeWithText("Download scheduled", substring = true).assertIsDisplayed()
                }.isSuccess
            }

            server.joinAndRethrow()

            // The fixture socket closing is not a durable completion signal:
            // the generated worker must still promote the asset and persist
            // the Library record. Avoid re-clicking the navigation tab during
            // each Compose poll; navigate once after durable core completion.
            val completedFile = File(context.filesDir, "items/rmd-1504-share/video.mp4")
            compose.waitUntil(45_000) {
                completedFile.isFile &&
                    completedFile.length() == payload.size.toLong() &&
                    runCatching {
                        GeneratedUniffiCoreGateway.open(
                            File(context.filesDir, DATABASE_NAME).absolutePath,
                        ).use { gateway ->
                            gateway.listLibrary().value.orEmpty().any { item ->
                                item.completed && item.displayTitle == title
                            }
                        }
                    }.getOrDefault(false)
            }
            compose.onNodeWithText("Library").performClick()
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText(title).assertIsDisplayed()
            assertTrue(completedFile.isFile)

            // Exercise the real app navigation stack after entering through ACTION_SEND.
            compose.onNodeWithText("Add").performClick()
            compose.onNodeWithText("Video URL").assertIsDisplayed()
            compose.onNodeWithText(sharedUrl).assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess
            }
        }
    }

    private fun stopFixtureService() {
        context.stopService(Intent(context, DownloadForegroundService::class.java))
        DownloadForegroundService.connectivityObserverFactoryForTesting = null
        val notifications = context.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (
            notifications.activeNotifications.any { it.id == DownloadServicePolicy.NotificationId } &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            SystemClock.sleep(50L)
        }
        assertTrue(
            "foreground worker must stop before fixture database cleanup",
            notifications.activeNotifications.none { it.id == DownloadServicePolicy.NotificationId },
        )
    }

    private fun deleteRuntimeState() {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name == "items" || file.name.startsWith(DATABASE_NAME)) file.deleteRecursively()
        }
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
        private val title: String,
        private val mediaUrl: String,
        private val expectedBytes: Int,
    ) : AppDownloadControlGateway {
        override fun enqueue(jobId: String): CoreGatewayResult<Boolean> =
            enqueue(jobId, DownloadSelectionOptions("fixture-720p"))

        override fun enqueue(jobId: String, choiceId: String?): CoreGatewayResult<Boolean> =
            enqueue(jobId, DownloadSelectionOptions(choiceId))

        override fun enqueue(jobId: String, options: DownloadSelectionOptions): CoreGatewayResult<Boolean> {
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
                    "media_id": "rmd-1504-share",
                    "canonical_url": "$sourceUrl"
                  },
                  "title": "$title",
                  "quality": {
                    "choice_id": "fixture-720p",
                    "label": "Fixture 720p",
                    "estimated_bytes": $expectedBytes,
                    "video_height": 720,
                    "audio_only": false,
                    "compatibility": "Preferred"
                  },
                  "assets": [
                    {
                      "asset_id": "video",
                      "kind": "Video",
                      "url": "$mediaUrl",
                      "relative_path": "items/rmd-1504-share/video.mp4",
                      "expected_bytes": $expectedBytes,
                      "expected_sha256": null,
                      "mime_type": "video/mp4"
                    }
                  ]
                }
            """.trimIndent()
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.beginTransaction()
                try {
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
                        put("display_title", title)
                    },
                )
                db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    private class OneShotFixtureServer(private val body: ByteArray) {
        private val failure = AtomicReference<Throwable?>()
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = 30_000
        }
        private lateinit var worker: Thread
        val mediaUrl = "http://127.0.0.1:" + server.localPort + "/video.mp4"

        fun start() {
            worker = thread(start = true, name = "rmd-1504-share-fixture") {
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
                                    "Content-Type: video/mp4\r\n" +
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
                    failure.set(AssertionError("share fixture server was not contacted", error))
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
        }

        fun joinAndRethrow() {
            worker.join(30_000)
            assertTrue("share fixture server should finish", !worker.isAlive)
            failure.get()?.let { throw AssertionError("share fixture server failed", it) }
        }
    }

    private companion object {
        const val DATABASE_NAME = "offline-yt-player.sqlite3"
    }
}
