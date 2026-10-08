package com.ekkus.offlineytplayer.downloads

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1506ConnectivityE2EInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = File(context.filesDir, DATABASE_NAME)

    @After
    fun cleanRuntimeState() {
        deleteRuntimeState()
    }

    @Test
    fun activeTransferPausesForConnectivityAndWifiOnlyResumesOnlyOnUnmeteredNetwork() {
        deleteRuntimeState()
        val jobId = "rmd-1506-connectivity"
        val payload = ByteArray(512 * 1024) { index -> (index % 251).toByte() }
        val relativePath = "items/rmd-1506-connectivity/video.mp4"
        val partialPath = File(context.filesDir, "items/rmd-1506-connectivity/.video.mp4.partial")
        val finalPath = File(context.filesDir, relativePath)
        val server = SlowRangeFixtureServer(payload).also { it.start() }

        try {
            GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { controls ->
                val enqueued = controls.enqueue(jobId)
                assertNull(enqueued.error)
                assertEquals(true, enqueued.value)
            }
            seedDurableWork(
                jobId = jobId,
                mediaUrl = server.mediaUrl,
                relativePath = relativePath,
                expectedBytes = payload.size,
            )

            val firstWorker = thread(start = true, name = "rmd-1506-first-worker") {
                DownloadWorkerExecutor.execute(context, jobId)
            }
            waitForState(jobId, CoreDownloadState.DOWNLOADING)
            waitForPartialBytes(partialPath)

            val pauses = InMemoryDownloadConnectivityPauseRegistry()
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { controls ->
                    val coordinator = DownloadConnectivityCoordinator(
                        coreGateway = core,
                        controlGateway = controls,
                        networkPreference = { DownloadNetworkPreference.WifiOnly },
                        connectivityPauseRegistry = pauses,
                    )

                    val disconnected = coordinator.onConnectivityChanged(DownloadConnectivity.None)
                    assertEquals(DownloadNetworkDecision.PauseForConnectivity, disconnected.decision)
                    assertEquals(listOf(jobId), disconnected.pausedJobIds)
                    assertTrue(pauses.wasPausedByConnectivity(jobId))

                    firstWorker.join(15_000)
                    assertFalse("worker must settle after durable connectivity pause", firstWorker.isAlive)
                    waitForState(jobId, CoreDownloadState.PAUSED)
                    assertTrue("paused transfer must retain real partial bytes", partialPath.length() > 0)
                    assertFalse("paused transfer must not promote a final asset", finalPath.exists())

                    val metered = coordinator.onConnectivityChanged(DownloadConnectivity.Metered)
                    assertEquals(DownloadNetworkDecision.PauseForConnectivity, metered.decision)
                    assertTrue(metered.resumedJobIds.isEmpty())
                    waitForState(jobId, CoreDownloadState.PAUSED)

                    val restored = coordinator.onConnectivityChanged(DownloadConnectivity.Unmetered)
                    assertEquals(DownloadNetworkDecision.Allow, restored.decision)
                    assertEquals(listOf(jobId), restored.resumedJobIds)
                    assertFalse(pauses.wasPausedByConnectivity(jobId))
                    waitForState(jobId, CoreDownloadState.QUEUED)
                }
            }

            val resumedWorker = thread(start = true, name = "rmd-1506-resumed-worker") {
                DownloadWorkerExecutor.execute(context, jobId)
            }
            waitForState(jobId, CoreDownloadState.COMPLETED)
            resumedWorker.join(15_000)
            assertFalse("resumed worker must complete", resumedWorker.isAlive)

            assertEquals(payload.toList(), finalPath.readBytes().toList())
            assertFalse("successful recovery must remove the partial file", partialPath.exists())
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val library = core.listLibrary()
                assertNull(library.error)
                val item = requireNotNull(library.value).single { it.itemId == jobId }
                assertTrue(item.completed)
            }
        } finally {
            server.close()
        }
    }

    private fun seedDurableWork(
        jobId: String,
        mediaUrl: String,
        relativePath: String,
        expectedBytes: Int,
    ) {
        val planJson = """
            {
              "source": {
                "provider": "direct-fixture",
                "media_id": "rmd-1506-connectivity",
                "canonical_url": "https://fixture.invalid/rmd-1506-connectivity"
              },
              "title": "RMD-1506 connectivity fixture",
              "quality": {
                "choice_id": "fixture-720p",
                "label": "720p",
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
                  "relative_path": "$relativePath",
                  "expected_bytes": $expectedBytes,
                  "expected_sha256": null,
                  "mime_type": "video/mp4"
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
                    put("display_title", "RMD-1506 connectivity fixture")
                },
            )
        }
    }

    private fun waitForPartialBytes(partialPath: File) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (partialPath.isFile && partialPath.length() > 0) return
            SystemClock.sleep(25)
        }
        throw AssertionError("transfer never persisted partial fixture bytes")
    }

    private fun waitForState(jobId: String, expected: CoreDownloadState) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var last: CoreDownloadState? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val queue = core.listDownloadQueue()
                assertNull(queue.error)
                last = queue.value?.firstOrNull { it.jobId == jobId }?.state
            }
            if (last == expected) return
            if (last == CoreDownloadState.FAILED || last == CoreDownloadState.CANCELED) {
                throw AssertionError("job entered terminal state $last while waiting for $expected")
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("timed out waiting for $expected; last=$last")
    }

    private fun deleteRuntimeState() {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name == "items" || file.name.startsWith(DATABASE_NAME)) {
                file.deleteRecursively()
            }
        }
    }

    private class SlowRangeFixtureServer(private val body: ByteArray) : AutoCloseable {
        private val stopping = AtomicBoolean(false)
        private val failure = AtomicReference<Throwable?>()
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = 500
        }
        private lateinit var worker: Thread
        val mediaUrl: String = "http://127.0.0.1:" + server.localPort + "/video.mp4"

        fun start() {
            worker = thread(start = true, name = "rmd-1506-range-server") {
                try {
                    while (!stopping.get()) {
                        val client = try {
                            server.accept()
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        client.use { socket ->
                            try {
                                val input = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                                val requestLine = input.readLine() ?: return@use
                                val headers = mutableMapOf<String, String>()
                                while (true) {
                                    val line = input.readLine() ?: break
                                    if (line.isEmpty()) break
                                    val split = line.indexOf(':')
                                    if (split > 0) {
                                        headers[line.substring(0, split).trim().lowercase()] =
                                            line.substring(split + 1).trim()
                                    }
                                }
                                if (!requestLine.contains("/video.mp4")) {
                                    error("unexpected fixture request: $requestLine")
                                }
                                val start = headers["range"]
                                    ?.removePrefix("bytes=")
                                    ?.substringBefore('-')
                                    ?.toIntOrNull()
                                    ?.coerceIn(0, body.size)
                                    ?: 0
                                val responseBody = body.copyOfRange(start, body.size)
                                val status = if (start > 0) "206 Partial Content" else "200 OK"
                                val contentRange = if (start > 0) {
                                    "Content-Range: bytes $start-${body.lastIndex}/${body.size}\r\n"
                                } else {
                                    ""
                                }
                                val responseHeaders =
                                    "HTTP/1.1 $status\r\n" +
                                        "Content-Type: video/mp4\r\n" +
                                        "Content-Length: ${responseBody.size}\r\n" +
                                        "ETag: \"rmd-1506-v1\"\r\n" +
                                        contentRange +
                                        "Connection: close\r\n\r\n"
                                val output = socket.getOutputStream()
                                output.write(responseHeaders.toByteArray(StandardCharsets.US_ASCII))
                                output.flush()
                                for (offset in responseBody.indices step CHUNK_BYTES) {
                                    val end = minOf(offset + CHUNK_BYTES, responseBody.size)
                                    try {
                                        output.write(responseBody, offset, end - offset)
                                        output.flush()
                                    } catch (_: SocketException) {
                                        break
                                    }
                                    Thread.sleep(CHUNK_DELAY_MS)
                                }
                            } catch (_: SocketException) {
                                // The paused worker intentionally closes its first transfer socket.
                            }
                        }
                    }
                } catch (error: Throwable) {
                    if (!stopping.get()) failure.set(error)
                }
            }
        }

        override fun close() {
            stopping.set(true)
            runCatching { server.close() }
            if (::worker.isInitialized) {
                worker.join(5_000)
                assertFalse("fixture server must stop", worker.isAlive)
            }
            failure.get()?.let { throw AssertionError("fixture server failed", it) }
        }

        private companion object {
            const val CHUNK_BYTES = 4 * 1024
            const val CHUNK_DELAY_MS = 10L
        }
    }

    private companion object {
        const val DATABASE_NAME = "offline-yt-player.sqlite3"
    }
}
