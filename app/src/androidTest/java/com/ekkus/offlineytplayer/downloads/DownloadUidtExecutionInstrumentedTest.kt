package com.ekkus.offlineytplayer.downloads

import android.app.job.JobScheduler
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.ParcelFileDescriptor
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
        val jobId = "rmd-502a-uidt-completion"
        val itemDir = context.filesDir.resolve("items/$jobId")
        database.delete()
        itemDir.deleteRecursively()
        val payload = "uidt fixture bytes".toByteArray()
        val connectionStarted = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val mediaUrl = "http://127.0.0.1:${server.localPort}/media.mp4"
        val serverThread = thread(start = true, name = "rmd-502a-uidt-server") {
            try {
                server.accept().use { socket ->
                connectionStarted.countDown()
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

        val jobs = context.getSystemService(JobScheduler::class.java)
        var scheduledJobId: Int? = null
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
            scheduledJobId = scheduled.jobId
            // An instrumentation process is not necessarily a foreground user gesture.
            // If the emulator defers this accepted UIDT job, force its real JobService
            // through JobScheduler rather than mistaking scheduler latency for a
            // native-worker failure. The transfer/Library assertions still run.
            val forcedStart = if (!connectionStarted.await(5, TimeUnit.SECONDS)) {
                forceScheduledJobToRun(context.packageName, requireNotNull(scheduledJobId))
            } else null

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
                assertEquals("UIDT job did not complete; forced start: $forcedStart", CoreDownloadState.COMPLETED, state)
                val item = gateway.getLibraryItemAsync(jobId).get(10, TimeUnit.SECONDS)
                assertNull(item.error)
                assertEquals(jobId, item.value?.itemId)
                assertEquals(true, item.value?.completed)
            }
            assertEquals(payload.size.toLong(), itemDir.resolve("video.mp4").length())
        } finally {
            scheduledJobId?.let { jobs.cancel(it) }
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
        val id = "rmd-502a-uidt-stop-retry"
        val itemDir = context.filesDir.resolve("items/$id")
        val bytes = ByteArray(256 * 1024) { (it % 251).toByte() }
        val firstChunk = CountDownLatch(1)
        val retryConnectionStarted = CountDownLatch(1)
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 45_000
        val serverThread = thread(start = true) {
            repeat(2) { attempt ->
                try {
                    server.accept().use { socket ->
                        if (attempt == 1) retryConnectionStarted.countDown()
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
                                // Keep the first transfer in flight long enough for
                                // cancellation even on heavily loaded emulators.
                                Thread.sleep(100)
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
            val forcedStart = if (!firstChunk.await(5, TimeUnit.SECONDS)) {
                forceScheduledJobToRun(context.packageName, requireNotNull(jobNumber))
            } else null
            assertTrue("UIDT first chunk never arrived; forced start: $forcedStart", firstChunk.await(20, TimeUnit.SECONDS))
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
                val forcedRetry = if (!retryConnectionStarted.await(5, TimeUnit.SECONDS)) {
                    forceScheduledJobToRun(context.packageName, requireNotNull(jobNumber))
                } else null
                assertTrue("UIDT retry never connected; forced retry: $forcedRetry", retryConnectionStarted.await(20, TimeUnit.SECONDS))
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

    private fun forceScheduledJobToRun(packageName: String, jobId: Int): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "cmd jobscheduler run -f $packageName $jobId",
            ),
        ).bufferedReader().use { it.readText().trim() }

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
                    put("plan_json", durablePlanJson(mediaUrl, bytes, jobId))
                    put("created_at_epoch_ms", System.currentTimeMillis())
                },
            )
        }
    }

    private fun durablePlanJson(mediaUrl: String, bytes: Int, jobId: String): String = """
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
              "relative_path": "items/$jobId/video.mp4",
              "expected_bytes": $bytes,
              "expected_sha256": null,
              "mime_type": "video/mp4"
            }
          ]
        }
    """.trimIndent()
}
