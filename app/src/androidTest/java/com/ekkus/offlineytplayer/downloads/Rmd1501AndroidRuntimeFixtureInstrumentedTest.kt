package com.ekkus.offlineytplayer.downloads

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.database.sqlite.SQLiteDatabase
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiLibraryPlaybackGateway
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1501AndroidRuntimeFixtureInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = File(context.filesDir, DATABASE_NAME)

    @After
    fun cleanRuntimeState() {
        deleteRuntimeState()
    }

    @Test
    fun generatedWorkerExecutesDeterministicFixtureIntoCompletedLibraryState() {
        deleteRuntimeState()
        val jobId = "rmd-1501-android-runtime-fixture"
        val mediaBytes = "deterministic android runtime fixture media bytes\n".toByteArray()
        val server = OneShotHttpServer(mediaBytes).also { it.start() }
        val sourceUrl = "${server.baseUrl}/fixture-source"
        val mediaUrl = "${server.baseUrl}/media.mp4"

        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { control ->
            val result = control.enqueue(jobId)
            assertNull(result.error)
            assertEquals(true, result.value)
        }
        seedDurableWork(
            jobId = jobId,
            sourceUrl = sourceUrl,
            mediaUrl = mediaUrl,
            expectedBytes = mediaBytes.size,
        )

        assertTrue("production Android worker executor should claim and complete fixture work", DownloadWorkerExecutor.execute(context, jobId))
        server.joinAndRethrow()

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val queue = core.listDownloadQueue()
            assertNull(queue.error)
            val snapshot = requireNotNull(queue.value).single { it.jobId == jobId }
            assertEquals(CoreDownloadState.COMPLETED, snapshot.state)
            assertEquals(mediaBytes.size.toLong(), snapshot.bytesDownloaded)
            assertEquals(mediaBytes.size.toLong(), snapshot.totalBytes)

            val library = core.listLibrary()
            assertNull(library.error)
            val item = requireNotNull(library.value).single { it.itemId == jobId }
            assertTrue(item.completed)
            assertEquals("RMD-1501 Android runtime fixture", item.displayTitle)
            assertEquals("720p", item.qualityLabel)
        }

        val localAsset = File(context.filesDir, "items/rmd-1501-android-runtime/rmd-1501-android-runtime.mp4")
        assertTrue("worker should persist the fixture asset under the production library root", localAsset.isFile)
        assertEquals(mediaBytes.size.toLong(), localAsset.length())
        assertArrayEquals(mediaBytes, localAsset.readBytes())

        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val assets = playback.listPlaybackAssets()
            assertNull(assets.error)
            val descriptor = requireNotNull(assets.value).single { it.itemId == jobId }
            assertTrue("completed fixture must be exported as a local playback asset", descriptor.playable)
            assertEquals("items/rmd-1501-android-runtime/rmd-1501-android-runtime.mp4", descriptor.videoRelativePath)
        }
    }

    @Test
    fun api29ForegroundSchedulerDispatchesFixtureThroughProductionService() {
        // This complements the direct worker test: scheduling must actually launch the
        // Android foreground-service fallback, not just invoke the worker from the test.
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        deleteRuntimeState()
        val jobId = "rmd-1501-scheduled-android-fixture"
        val mediaBytes = "scheduled foreground-service fixture media bytes\n".toByteArray()
        val server = OneShotHttpServer(mediaBytes).also { it.start() }
        val sourceUrl = "${server.baseUrl}/fixture-source"
        val mediaUrl = "${server.baseUrl}/media.mp4"

        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { control ->
            val result = control.enqueue(jobId)
            assertNull(result.error)
            assertEquals(true, result.value)
        }
        seedDurableWork(jobId, sourceUrl, mediaUrl, mediaBytes.size)

        val scheduled = AndroidDownloadExecutionScheduler(context).schedule(
            DownloadScheduleRequest(queueItemId = jobId),
        )
        assertEquals(DownloadSchedulerKind.ForegroundServiceFallback, scheduled.kind)
        assertTrue("foreground-service scheduler should accept fixture work", scheduled.accepted)

        var state: CoreDownloadState? = null
        val deadline = android.os.SystemClock.elapsedRealtime() + 60_000L
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val queue = core.listDownloadQueue()
                assertNull(queue.error)
                state = queue.value?.firstOrNull { it.jobId == jobId }?.state
            }
            if (state == CoreDownloadState.COMPLETED || state == CoreDownloadState.FAILED) break
            Thread.sleep(250)
        }
        assertEquals("scheduled service must complete durable fixture work", CoreDownloadState.COMPLETED, state)
        server.joinAndRethrow()

        val asset = File(context.filesDir, "items/rmd-1501-android-runtime/rmd-1501-android-runtime.mp4")
        assertTrue("scheduled worker must persist the local media asset", asset.isFile)
        assertArrayEquals(mediaBytes, asset.readBytes())
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val descriptors = playback.listPlaybackAssets()
            assertNull(descriptors.error)
            assertTrue(requireNotNull(descriptors.value).any { it.itemId == jobId && it.playable })
        }
    }


    @Test
    fun scheduledFixtureReopensOfflineAndPlaysViaCanonicalMediaSession() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT in 26..33)
        deleteRuntimeState()
        val jobId = "rmd-1501-offline-session-wave"
        val wave = deterministicWaveBytes()
        val server = OneShotHttpServer(wave, "audio/wav").also { it.start() }
        val sourceUrl = "${server.baseUrl}/fixture-source"
        val mediaUrl = "${server.baseUrl}/offline.wav"
        val relativePath = "items/rmd-1501-android-runtime/offline.wav"

        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { control ->
            val enqueued = control.enqueue(jobId)
            assertNull(enqueued.error)
            assertEquals(true, enqueued.value)
        }
        seedDurableWork(
            jobId, sourceUrl, mediaUrl, wave.size,
            relativePath = relativePath,
            mimeType = "audio/wav",
        )
        val scheduled = AndroidDownloadExecutionScheduler(context).schedule(DownloadScheduleRequest(jobId))
        assertEquals(DownloadSchedulerKind.ForegroundServiceFallback, scheduled.kind)
        assertTrue("production scheduler should accept the offline fixture", scheduled.accepted)

        var state: CoreDownloadState? = null
        val downloadDeadline = SystemClock.elapsedRealtime() + 60_000L
        while (SystemClock.elapsedRealtime() < downloadDeadline) {
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val queue = core.listDownloadQueue()
                assertNull(queue.error)
                state = queue.value?.firstOrNull { it.jobId == jobId }?.state
            }
            if (state == CoreDownloadState.COMPLETED || state == CoreDownloadState.FAILED) break
            Thread.sleep(250)
        }
        assertEquals("production scheduler must complete the local fixture", CoreDownloadState.COMPLETED, state)
        server.joinAndRethrow() // The only fixture HTTP server is now closed.

        // Reopen the durable gateways as a cold repository reader with no source server.
        val localAsset = File(context.filesDir, relativePath)
        assertTrue(localAsset.isFile)
        assertArrayEquals(wave, localAsset.readBytes())
        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val library = core.listLibrary()
            assertNull(library.error)
            assertTrue(requireNotNull(library.value).any { it.itemId == jobId && it.completed })
        }
        GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
            val assets = playback.listPlaybackAssets()
            assertNull(assets.error)
            val descriptor = requireNotNull(assets.value).single { it.itemId == jobId }
            assertTrue(descriptor.playable)
            assertEquals(relativePath, descriptor.videoRelativePath)
        }

        // The production MediaSessionService/ExoPlayer must prepare and play the
        // downloaded local file after the fixture server has been shut down.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.fromUri(Uri.fromFile(localAsset)))
                controller.prepare()
                controller.play()
            }
            var ready = false
            val playbackDeadline = SystemClock.elapsedRealtime() + 10_000L
            while (SystemClock.elapsedRealtime() < playbackDeadline) {
                instrumentation.runOnMainSync {
                    ready = controller.playbackState == Player.STATE_READY &&
                        controller.playWhenReady &&
                        controller.currentMediaItem?.localConfiguration?.uri == Uri.fromFile(localAsset)
                }
                if (ready) break
                SystemClock.sleep(50)
            }
            assertTrue("canonical session must play the downloaded local fixture offline", ready)
        } finally {
            instrumentation.runOnMainSync {
                controller.pause()
                controller.clearMediaItems()
                controller.release()
            }
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

    private fun seedDurableWork(
        jobId: String,
        sourceUrl: String,
        mediaUrl: String,
        expectedBytes: Int,
        relativePath: String = "items/rmd-1501-android-runtime/rmd-1501-android-runtime.mp4",
        mimeType: String = "video/mp4",
    ) {
        val planJson = """
            {
              "source": {
                "provider": "direct-fixture",
                "media_id": "rmd-1501-android-runtime",
                "canonical_url": "$sourceUrl"
              },
              "title": "RMD-1501 Android runtime fixture",
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
                  "asset_id": "combined",
                  "kind": "Video",
                  "url": "$mediaUrl",
                  "relative_path": "$relativePath",
                  "expected_bytes": $expectedBytes,
                  "expected_sha256": null,
                  "mime_type": "$mimeType"
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
                """
                INSERT OR REPLACE INTO download_work_items(job_id, plan_json, created_at_epoch_ms)
                VALUES(?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(jobId, planJson, 1_700_000_000_000L),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO download_presentations(job_id, display_title)
                VALUES(?, ?)
                """.trimIndent(),
                arrayOf(jobId, "RMD-1501 Android runtime fixture"),
            )
        }
    }

    private fun deleteRuntimeState() {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name == "items" || file.name.startsWith(DATABASE_NAME)) {
                file.deleteRecursively()
            }
        }
    }

    private class OneShotHttpServer(private val body: ByteArray, private val contentType: String = "video/mp4") {
        private val failure = AtomicReference<Throwable?>()
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = 15_000
        }
        private lateinit var thread: Thread

        val baseUrl: String = "http://127.0.0.1:${server.localPort}"

        fun start() {
            thread = Thread({
                try {
                    server.use { socket ->
                        val client = socket.accept()
                        client.use {
                            val input = it.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                            while (true) {
                                val line = input.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            val header = "HTTP/1.1 200 OK\r\nContent-Type: ${contentType}\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                            it.getOutputStream().use { output ->
                                output.write(header.toByteArray(StandardCharsets.US_ASCII))
                                output.write(body)
                                output.flush()
                            }
                        }
                    }
                } catch (error: SocketTimeoutException) {
                    failure.set(AssertionError("fixture media server did not receive a request", error))
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }, "rmd-1501-fixture-http")
            thread.start()
        }

        fun joinAndRethrow() {
            thread.join(15_000)
            assertTrue("fixture media server thread should finish", !thread.isAlive)
            failure.get()?.let { throw AssertionError("fixture media server failed", it) }
        }
    }

    private companion object {
        const val DATABASE_NAME = "offline-yt-player.sqlite3"
    }
}
