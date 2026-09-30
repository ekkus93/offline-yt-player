package com.ekkus.offlineytplayer.downloads

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import java.net.InetAddress
import java.net.ServerSocket
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
