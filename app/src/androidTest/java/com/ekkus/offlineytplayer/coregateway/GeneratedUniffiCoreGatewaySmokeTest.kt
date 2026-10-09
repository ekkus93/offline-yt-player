package com.ekkus.offlineytplayer.coregateway

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.app.NotificationManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.downloads.AndroidDownloadExecutionScheduler
import com.ekkus.offlineytplayer.downloads.DownloadConnectivity
import com.ekkus.offlineytplayer.downloads.DownloadForegroundService
import com.ekkus.offlineytplayer.downloads.DownloadServicePolicy
import com.ekkus.offlineytplayer.downloads.DownloadNetworkPreference
import com.ekkus.offlineytplayer.downloads.DownloadScheduleRequest
import com.ekkus.offlineytplayer.downloads.DownloadSchedulerKind
import com.ekkus.offlineytplayer.playback.PlaybackSessionService
import java.io.Closeable
import java.net.InetAddress
import java.net.SocketException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneratedUniffiCoreGatewaySmokeTest {
    @Test
    fun packagedRustLibraryAndGeneratedGatewayRoundTripAppPrivateDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.cacheDir.resolve("rmd-204-${System.nanoTime()}").apply { mkdirs() }
        val database = root.resolve("library.sqlite")

        try {
            System.loadLibrary("offline_yt_core")
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { gateway ->
                GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { controls ->
                    val listed = gateway.listLibraryAsync().get(10, TimeUnit.SECONDS)
                    assertEquals(emptyList<CoreLibraryItem>(), listed.value)
                    assertNull(listed.error)

                    val emptyQueue = gateway.listDownloadQueueAsync().get(10, TimeUnit.SECONDS)
                    assertEquals(emptyList<CoreDownloadSnapshot>(), emptyQueue.value)
                    assertNull(emptyQueue.error)

                    val invalid = controls.enqueueAsync("   ").get(10, TimeUnit.SECONDS)
                    assertFalse(invalid.value ?: true)
                    assertNotNull(invalid.error)
                    assertFalse(invalid.error?.retryable ?: true)
                    assertTrue(invalid.error?.message?.contains("must not be empty") == true)

                    val enqueued = controls.enqueueAsync("job-smoke").get(10, TimeUnit.SECONDS)
                    assertEquals(true, enqueued.value)
                    assertNull(enqueued.error)

                    val queued = gateway.listDownloadQueueAsync().get(10, TimeUnit.SECONDS)
                    assertEquals(1, queued.value?.size)
                    assertEquals(CoreDownloadState.QUEUED, queued.value?.single()?.state)
                    assertNull(queued.error)

                    val paused = controls.pauseAsync("job-smoke").get(10, TimeUnit.SECONDS)
                    assertEquals(true, paused.value)
                    assertNull(paused.error)

                    val pausedQueue = gateway.listDownloadQueueAsync().get(10, TimeUnit.SECONDS)
                    assertEquals(CoreDownloadState.PAUSED, pausedQueue.value?.single()?.state)
                    assertNull(pausedQueue.error)

                    val missing = gateway.getLibraryItemAsync("missing-item").get(10, TimeUnit.SECONDS)
                    assertNull(missing.value)
                    assertNull(missing.error)

                    val deleted = gateway.deleteLibraryItemAsync("missing-item").get(10, TimeUnit.SECONDS)
                    assertFalse(deleted.value ?: true)
                    assertNull(deleted.error)
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun androidSchedulerFallbackCompletesDurableFixtureIntoLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Stop any service left running by a prior instrumentation test before
        // deleting the shared database or installing this fixture's callback.
        context.stopService(Intent(context, DownloadForegroundService::class.java))
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val stopDeadline = SystemClock.elapsedRealtime() + 5_000
        while (
            notificationManager.activeNotifications.any { it.id == DownloadServicePolicy.NotificationId } &&
            SystemClock.elapsedRealtime() < stopDeadline
        ) {
            SystemClock.sleep(50)
        }
        assertFalse(
            "previous foreground service must stop before fixture setup",
            notificationManager.activeNotifications.any { it.id == DownloadServicePolicy.NotificationId },
        )
        val database = context.filesDir.resolve("offline-yt-player.sqlite3")
        val itemDir = context.filesDir.resolve("items/rmd-502a-foreground")
        database.delete()
        itemDir.deleteRecursively()
        val jobId = "rmd-502a-foreground-fixture"
        val payload = "foreground fixture bytes".toByteArray()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val mediaUrl = listOf("http", "://", "127.0.0.1:", server.localPort.toString(), "/media.mp4").joinToString("")
        val serverThread = thread(start = true, name = "rmd-502a-fixture-server") {
            try {
                server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val headers = "HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nContent-Type: video/mp4\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().use { output ->
                    output.write(headers.toByteArray())
                    output.write(payload)
                    output.flush()
                }
                }
            } catch (error: SocketException) {
                // Cleanup may close an uncontacted listener after the test has
                // already failed. Do not let a background exception hide the
                // original queue/worker failure from the test report.
                if (!server.isClosed) throw error
            }
        }

        // This suite validates the packaged scheduler/UniFFI worker, not Android's
        // real network callbacks (covered separately by RMD-1506). A software-emulated
        // CI device can report no default network despite its reachable loopback fixture.
        DownloadForegroundService.connectivityObserverFactoryForTesting = { _, onChanged ->
            onChanged(DownloadConnectivity.Unmetered)
            Closeable { }
        }
        try {
            System.loadLibrary("offline_yt_core")
            GeneratedUniffiCoreGateway.open(database.absolutePath).close()
            seedDurableFixtureWork(database.absolutePath, jobId, mediaUrl, payload.size)

            val scheduled = AndroidDownloadExecutionScheduler(context).schedule(
                DownloadScheduleRequest(
                    queueItemId = jobId,
                    estimatedDownloadBytes = payload.size.toLong(),
                    networkPreference = DownloadNetworkPreference.AnyNetwork,
                ),
            )
            assertEquals(DownloadSchedulerKind.ForegroundServiceFallback, scheduled.kind)
            assertTrue(scheduled.accepted)

            GeneratedUniffiCoreGateway.open(database.absolutePath).use { gateway ->
                val completed = waitForCompletedFixture(gateway, jobId)
                assertEquals(CoreDownloadState.COMPLETED, completed.state)
                val item = gateway.getLibraryItemAsync(jobId).get(10, TimeUnit.SECONDS)
                assertNull(item.error)
                assertEquals(jobId, item.value?.itemId)
                assertEquals(true, item.value?.completed)
            }
            assertEquals(payload.size.toLong(), itemDir.resolve("video.mp4").length())

            val playbackAssets = GeneratedUniffiLibraryPlaybackGateway.open(database.absolutePath).use { playback ->
                playback.listPlaybackAssetsAsync().get(10, TimeUnit.SECONDS)
            }
            assertNull(playbackAssets.error)
            val playbackAsset = playbackAssets.value.orEmpty().single { it.itemId == jobId }
            assertTrue(playbackAsset.playable)
            assertEquals("items/rmd-502a-foreground/video.mp4", playbackAsset.videoRelativePath)
            assertNull(playbackAsset.audioRelativePath)
            assertNull(playbackAsset.unavailableReason)
            val localVideoPath = context.filesDir.resolve(playbackAsset.videoRelativePath!!).absolutePath
            assertEquals(itemDir.resolve("video.mp4").absolutePath, localVideoPath)
            assertCompletedLibraryItemRoutesToMediaSession(context, jobId, localVideoPath)

        } finally {
            context.stopService(android.content.Intent(context, DownloadForegroundService::class.java))
            DownloadForegroundService.connectivityObserverFactoryForTesting = null
            server.close()
            serverThread.join(1_000)
            itemDir.deleteRecursively()
            database.delete()
        }
    }

    private fun seedDurableFixtureWork(databasePath: String, jobId: String, mediaUrl: String, bytes: Int) {
        val database = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE)
        database.use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS download_work_items (" +
                    "job_id TEXT PRIMARY KEY," +
                    "plan_json TEXT NOT NULL," +
                    "created_at_epoch_ms INTEGER NOT NULL" +
                    ")",
            )
            db.insertOrThrow(
                "download_jobs",
                null,
                ContentValues().apply {
                    put("job_id", jobId)
                    put("state_json", "\"Queued\"")
                    put("bytes_downloaded", 0L)
                    put("total_bytes", bytes.toLong())
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
                    put("plan_json", durablePlanJson(mediaUrl, bytes))
                    put("created_at_epoch_ms", System.currentTimeMillis())
                },
            )
        }
    }

    private fun durablePlanJson(mediaUrl: String, bytes: Int): String = """
        {
          "source": {"provider": "direct-fixture", "media_id": "rmd-502a-foreground", "canonical_url": null},
          "title": "RMD 502a foreground fixture",
          "quality": {
            "choice_id": "fixture-720p",
            "label": "720p",
            "estimated_bytes": $bytes,
            "video_height": 720,
            "audio_only": false,
            "compatibility": "Preferred"
          },
          "assets": [
            {
              "asset_id": "video",
              "kind": "Video",
              "url": "$mediaUrl",
              "relative_path": "items/rmd-502a-foreground/video.mp4",
              "expected_bytes": $bytes,
              "expected_sha256": null,
              "mime_type": "video/mp4"
            }
          ]
        }
    """.trimIndent()

    private fun waitForCompletedFixture(
        gateway: GeneratedUniffiCoreGateway,
        jobId: String,
    ): CoreDownloadSnapshot {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        var last: CoreDownloadSnapshot? = null
        while (System.nanoTime() < deadline) {
            val queue = gateway.listDownloadQueueAsync().get(10, TimeUnit.SECONDS)
            assertNull(queue.error)
            last = queue.value.orEmpty().firstOrNull { it.jobId == jobId } ?: last
            if (last?.state == CoreDownloadState.COMPLETED) return last
            Thread.sleep(250)
        }
        throw AssertionError("Fixture job did not complete; last=$last")
    }

    private fun assertCompletedLibraryItemRoutesToMediaSession(
        context: android.content.Context,
        jobId: String,
        localVideoPath: String,
    ) {
        assertFalse(localVideoPath.startsWith("http://"))
        assertFalse(localVideoPath.startsWith("https://"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            val mediaItem = MediaItem.Builder()
                .setMediaId(jobId)
                .setUri(Uri.fromFile(context.filesDir.resolve(localVideoPath).takeUnless { it.isAbsolute } ?: java.io.File(localVideoPath)))
                .build()
            instrumentation.runOnMainSync {
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (SystemClock.elapsedRealtime() < deadline) {
                var currentMediaId: String? = null
                instrumentation.runOnMainSync { currentMediaId = controller.currentMediaItem?.mediaId }
                if (currentMediaId == jobId) return
                SystemClock.sleep(50)
            }
            instrumentation.runOnMainSync {
                assertEquals(jobId, controller.currentMediaItem?.mediaId)
            }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }
}
