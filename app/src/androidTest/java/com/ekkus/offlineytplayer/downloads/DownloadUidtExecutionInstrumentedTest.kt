package com.ekkus.offlineytplayer.downloads

import android.app.job.JobScheduler
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadUidtExecutionInstrumentedTest {
    @Test
    fun api35UidtSchedulerCompletesDurableFixtureIntoLibrary() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = context.filesDir.resolve("offline-yt-player.sqlite3")
        val itemDir = context.filesDir.resolve("items/rmd-502a-uidt")
        database.delete()
        itemDir.deleteRecursively()
        val jobId = "rmd-502a-uidt-fixture"
        val payload = "uidt fixture bytes".toByteArray()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val mediaUrl = "http://127.0.0.1:${server.localPort}/media.mp4"
        val serverThread = thread(start = true, name = "rmd-502a-uidt-server") {
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
            } catch (error: java.net.SocketException) {
                // Closing the fixture server during test cleanup interrupts accept().
                // That is expected when the job never connected; let the main test
                // report the actual scheduling/queue failure instead of replacing it
                // with an uncaught background-thread SocketException.
                if (!server.isClosed) throw error
            }
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
            assertEquals(DownloadSchedulerKind.UserInitiatedDataTransferJob, scheduled.kind)
            assertTrue(scheduled.accepted)
            assertTrue(scheduled.jobId != null)

            GeneratedUniffiCoreGateway.open(database.absolutePath).use { gateway ->
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                var state: CoreDownloadState? = null
                while (System.nanoTime() < deadline) {
                    val queue = gateway.listDownloadQueueAsync().get(10, TimeUnit.SECONDS)
                    assertNull(queue.error)
                    state = queue.value.orEmpty().firstOrNull { it.jobId == jobId }?.state ?: state
                    if (state == CoreDownloadState.COMPLETED) break
                    Thread.sleep(250)
                }
                assertEquals(CoreDownloadState.COMPLETED, state)
                val item = gateway.getLibraryItemAsync(jobId).get(10, TimeUnit.SECONDS)
                assertNull(item.error)
                assertEquals(jobId, item.value?.itemId)
                assertEquals(true, item.value?.completed)
            }
            assertEquals(payload.size.toLong(), itemDir.resolve("video.mp4").length())
        } finally {
            server.close()
            serverThread.join(1_000)
            itemDir.deleteRecursively()
            database.delete()
        }
    }


    @Test
    fun api35JobSchedulerStopHaltsNativeAndManualRetryCompletes() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = context.filesDir.resolve("offline-yt-player.sqlite3")
        val itemDir = context.filesDir.resolve("items/rmd-502a-uidt")
        val id = "rmd-502a-uidt-fixture"
        val bytes = ByteArray(256 * 1024) { (it % 251).toByte() }
        val firstChunk = CountDownLatch(1)
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 45_000
        val serverThread = thread(start = true) {
            repeat(2) { attempt ->
                try {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val output = socket.getOutputStream()
                        output.write(("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\n" +
                            "Content-Type: video/mp4\r\nConnection: close\r\n\r\n").toByteArray())
                        for (offset in bytes.indices step 1024) {
                            output.write(bytes, offset, minOf(1024, bytes.size - offset))
                            output.flush()
                            if (attempt == 0) {
                                firstChunk.countDown()
                                Thread.sleep(25)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // The first connection is expected to terminate on stop.
                }
            }
        }
        val jobs = context.getSystemService(JobScheduler::class.java)
        var jobNumber: Int? = null
        try {
            db.delete()
            itemDir.deleteRecursively()
            System.loadLibrary("offline_yt_core")
            GeneratedUniffiCoreGateway.open(db.absolutePath).close()
            seedDurableFixtureWork(db.absolutePath, id, "http://127.0.0.1:${server.localPort}/media.mp4", bytes.size)
            val scheduler = AndroidDownloadExecutionScheduler(context)
            val initial = scheduler.schedule(DownloadScheduleRequest(id, bytes.size.toLong()))
            assertTrue(initial.accepted)
            jobNumber = initial.jobId
            assertTrue(firstChunk.await(25, TimeUnit.SECONDS))
            jobs.cancel(requireNotNull(jobNumber))
            GeneratedUniffiCoreGateway.open(db.absolutePath).use { gateway ->
                fun waitFor(state: CoreDownloadState) {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40)
                    var observed: CoreDownloadState? = null
                    while (System.nanoTime() < deadline) {
                        val queue = gateway.listDownloadQueueAsync().get(10, TimeUnit.SECONDS)
                        assertNull(queue.error)
                        observed = queue.value.orEmpty().firstOrNull { it.jobId == id }?.state
                        if (observed == state) return
                        Thread.sleep(100)
                    }
                    assertEquals(state, observed)
                }
                waitFor(CoreDownloadState.RETRY_WAIT)
                assertTrue(!itemDir.resolve("video.mp4").exists())
                val restarted = scheduler.schedule(DownloadScheduleRequest(id, bytes.size.toLong()))
                assertTrue(restarted.accepted)
                jobNumber = restarted.jobId
                waitFor(CoreDownloadState.COMPLETED)
                assertTrue(bytes.contentEquals(itemDir.resolve("video.mp4").readBytes()))
            }
        } finally {
            jobNumber?.let { jobs.cancel(it) }
            server.close()
            serverThread.join(2_000)
            itemDir.deleteRecursively()
            db.delete()
        }
    }

    private fun seedDurableFixtureWork(databasePath: String, jobId: String, mediaUrl: String, bytes: Int) {
        SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
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
          "source": {"provider": "direct-fixture", "media_id": "rmd-502a-uidt", "canonical_url": null},
          "title": "RMD 502a UIDT fixture",
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
              "relative_path": "items/rmd-502a-uidt/video.mp4",
              "expected_bytes": $bytes,
              "expected_sha256": null,
              "mime_type": "video/mp4"
            }
          ]
        }
    """.trimIndent()
}
