package com.ekkus.offlineytplayer.downloads

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1506ConnectivityE2EInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = File(context.filesDir, DATABASE_NAME)

    @After
    fun cleanRuntimeState() {
        context.stopService(Intent(context, DownloadForegroundService::class.java))
        DownloadForegroundService.connectivityObserverFactoryForTesting = null
        deleteRuntimeState()
    }

    /**
     * Runs only from the host-controlled OS connectivity lane. Normal smoke
     * deliberately skips it: changing real device network state requires adb
     * while instrumentation is still running. The production service and
     * AndroidDownloadConnectivityObserver have no injected connectivity seam.
     */
    @Test
    fun hostDrivenOsConnectivityLossPausesAndRestoresForegroundTransfer() {
        assumeTrue(
            "requires host-driven adb network controls",
            InstrumentationRegistry.getArguments().getString("rmdHostNetworkTransitions") == "true",
        )
        deleteRuntimeState()
        DownloadForegroundService.connectivityObserverFactoryForTesting = null
        System.loadLibrary("offline_yt_core")
        val signals = File(requireNotNull(context.getExternalFilesDir(null)), "rmd1506-host")
            .apply { deleteRecursively(); mkdirs() }
        val observedNetwork = AtomicReference(DownloadConnectivity.None)
        val observer = AndroidDownloadConnectivityObserver(context, observedNetwork::set)
        val jobId = "rmd-1506-os-network"
        val payload = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        val relativePath = "items/rmd-1506-os-network/video.mp4"
        val partial = File(context.filesDir, "items/rmd-1506-os-network/.video.mp4.partial")
        val finished = File(context.filesDir, relativePath)
        val server = SlowRangeFixtureServer(payload).also { it.start() }
        observer.start()
        try {
            val initialDeadline = SystemClock.elapsedRealtime() + 20_000L
            while (
                observedNetwork.get() == DownloadConnectivity.None &&
                SystemClock.elapsedRealtime() < initialDeadline
            ) SystemClock.sleep(50)
            assertTrue(
                "host must supply a real default Android network before scheduling",
                observedNetwork.get() != DownloadConnectivity.None,
            )
            GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { controls ->
                val result = controls.enqueue(jobId)
                assertNull(result.error)
                assertEquals(true, result.value)
            }
            seedDurableWork(jobId, server.mediaUrl, relativePath, payload.size)
            assertTrue(
                AndroidDownloadExecutionScheduler(context).schedule(
                    DownloadScheduleRequest(jobId, networkPreference = DownloadNetworkPreference.AnyNetwork),
                ).accepted,
            )
            waitForState(jobId, CoreDownloadState.DOWNLOADING)
            waitForPartialBytes(partial)
            signals.resolve("ready").writeText("real network active, durable worker downloading")

            val disconnectDeadline = SystemClock.elapsedRealtime() + 45_000L
            while (
                observedNetwork.get() != DownloadConnectivity.None &&
                SystemClock.elapsedRealtime() < disconnectDeadline
            ) SystemClock.sleep(50)
            assertEquals(
                "adb must cause a real Android default-network loss callback",
                DownloadConnectivity.None,
                observedNetwork.get(),
            )
            waitForState(jobId, CoreDownloadState.PAUSED)
            assertTrue("network pause must retain resumable partial bytes", partial.length() > 0L)
            assertFalse("network pause must not promote completed media", finished.exists())
            signals.resolve("paused").writeText("actual OS callback durably paused foreground work")

            val reconnectDeadline = SystemClock.elapsedRealtime() + 45_000L
            while (
                observedNetwork.get() == DownloadConnectivity.None &&
                SystemClock.elapsedRealtime() < reconnectDeadline
            ) SystemClock.sleep(50)
            assertTrue(
                "adb must restore a real Android default network",
                observedNetwork.get() != DownloadConnectivity.None,
            )
            waitForState(jobId, CoreDownloadState.COMPLETED, timeoutMs = 90_000L)
            assertEquals(payload.size.toLong(), finished.length())
            assertArrayEquals(payload, finished.readBytes())
            assertFalse("resume must remove partial bytes after promotion", partial.exists())
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val library = core.listLibrary()
                assertNull(library.error)
                assertTrue(requireNotNull(library.value).any { it.itemId == jobId && it.completed })
            }
            signals.resolve("complete").writeText("real OS loss/resume completed durable local item")
        } finally {
            observer.close()
            context.stopService(Intent(context, DownloadForegroundService::class.java))
            server.close()
        }
    }


    /**
     * Host-only Android Wi-Fi-only acceptance: the host toggles the device's
     * saved Wi-Fi SSID metered override using cmd netpolicy. Unlike the JVM
     * policy tests this must observe actual ConnectivityManager callbacks and
     * real generated-core worker state transitions.
     */
    @Test
    fun hostDrivenWifiOnlyMeteredWifiPausesAndUnmeteredWifiResumes() {
        assumeTrue(
            "requires adb Wi-Fi metered override",
            InstrumentationRegistry.getArguments().getString("rmdHostWifiMetered") == "true",
        )
        deleteRuntimeState()
        DownloadForegroundService.connectivityObserverFactoryForTesting = null
        System.loadLibrary("offline_yt_core")
        val signals = File(requireNotNull(context.getExternalFilesDir(null)), "rmd1506-host")
            .apply { mkdirs() }
        val network = AtomicReference(DownloadConnectivity.None)
        val observer = AndroidDownloadConnectivityObserver(context, network::set)
        val jobId = "rmd-1506-wifi-metered"
        val payload = ByteArray(8 * 1024 * 1024) { (it % 247).toByte() }
        val relativePath = "items/rmd-1506-wifi-metered/video.mp4"
        val partial = File(context.filesDir, "items/rmd-1506-wifi-metered/.video.mp4.partial")
        val finished = File(context.filesDir, relativePath)
        val server = SlowRangeFixtureServer(payload).also { it.start() }
        observer.start()
        try {
            val networkDeadline = SystemClock.elapsedRealtime() + 20_000L
            while (
                network.get() != DownloadConnectivity.Unmetered &&
                SystemClock.elapsedRealtime() < networkDeadline
            ) SystemClock.sleep(50L)
            assertEquals(
                "host must expose a real unmetered Wi-Fi default network",
                DownloadConnectivity.Unmetered,
                network.get(),
            )
            GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { controls ->
                val enqueued = controls.enqueue(jobId)
                assertNull(enqueued.error)
                assertEquals(true, enqueued.value)
            }
            seedDurableWork(jobId, server.mediaUrl, relativePath, payload.size)
            assertTrue(
                AndroidDownloadExecutionScheduler(context).schedule(
                    DownloadScheduleRequest(
                        queueItemId = jobId,
                        networkPreference = DownloadNetworkPreference.WifiOnly,
                    ),
                ).accepted,
            )
            waitForState(jobId, CoreDownloadState.DOWNLOADING)
            waitForPartialBytes(partial)
            signals.resolve("wifi-ready").writeText("WifiOnly transfer started on genuine unmetered Wi-Fi")
            val meteredDeadline = SystemClock.elapsedRealtime() + 45_000L
            while (
                network.get() != DownloadConnectivity.Metered &&
                SystemClock.elapsedRealtime() < meteredDeadline
            ) SystemClock.sleep(50L)
            assertEquals(
                "host metered override must reach the real Android observer",
                DownloadConnectivity.Metered,
                network.get(),
            )
            waitForState(jobId, CoreDownloadState.PAUSED)
            assertTrue("metered Wi-Fi pause retains partial bytes", partial.length() > 0L)
            assertFalse("metered Wi-Fi pause cannot promote final bytes", finished.exists())
            signals.resolve("wifi-paused").writeText("real metered Wi-Fi paused WifiOnly durable transfer")
            val unmeteredDeadline = SystemClock.elapsedRealtime() + 45_000L
            while (
                network.get() != DownloadConnectivity.Unmetered &&
                SystemClock.elapsedRealtime() < unmeteredDeadline
            ) SystemClock.sleep(50L)
            assertEquals(
                "clearing metered override must restore the real eligible Wi-Fi callback",
                DownloadConnectivity.Unmetered,
                network.get(),
            )
            waitForState(jobId, CoreDownloadState.COMPLETED, timeoutMs = 90_000L)
            assertArrayEquals(payload, finished.readBytes())
            assertFalse("completed Wi-Fi-only download must remove partial", partial.exists())
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val library = core.listLibrary()
                assertNull(library.error)
                assertTrue(requireNotNull(library.value).any { it.itemId == jobId && it.completed })
            }
            signals.resolve("wifi-complete").writeText("real unmetered Wi-Fi resumed and completed Library item")
        } finally {
            observer.close()
            context.stopService(Intent(context, DownloadForegroundService::class.java))
            server.close()
        }
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

    @Test
    fun secondScheduleIntentDispatchesWorkWithAlreadyRegisteredConnectivityObserver() {
        deleteRuntimeState()
        val jobId = "rmd-1506-observer-reuse"
        val payload = ByteArray(16 * 1024) { index -> (index % 251).toByte() }
        val relativePath = "items/rmd-1506-observer-reuse/video.mp4"
        val server = SlowRangeFixtureServer(payload).also { it.start() }
        val serviceIntent = Intent(context, DownloadForegroundService::class.java)
        val registrations = AtomicInteger(0)
        val callbacks = AtomicInteger(0)
        DownloadForegroundService.connectivityObserverFactoryForTesting = { _, onChanged ->
            registrations.incrementAndGet()
            callbacks.incrementAndGet()
            onChanged(DownloadConnectivity.Unmetered)
            Closeable { }
        }
        try {
            // Register an observer that emits exactly one initial Unmetered event.
            // It never emits again, so only onStartCommand's observer-reuse path
            // can dispatch the newly queued fixture work.
            ContextCompat.startForegroundService(
                context,
                Intent(serviceIntent).setAction(DownloadForegroundService.ACTION_CONNECTIVITY_RETRY),
            )
            waitForServiceNotification()
            SystemClock.sleep(500)
            assertEquals("observer should register once before enqueuing", 1, registrations.get())
            assertEquals("initial network callback only", 1, callbacks.get())

            GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { controls ->
                val result = controls.enqueue(jobId)
                assertNull(result.error)
                assertEquals(true, result.value)
            }
            seedDurableWork(jobId, server.mediaUrl, relativePath, payload.size)
            ContextCompat.startForegroundService(
                context,
                Intent(serviceIntent)
                    .setAction(DownloadForegroundService.ACTION_SCHEDULE_WORK)
                    .putExtra(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, jobId),
            )
            waitForState(jobId, CoreDownloadState.COMPLETED)
            assertEquals(payload.toList(), File(context.filesDir, relativePath).readBytes().toList())
            assertEquals("registered observer must be reused", 1, registrations.get())
            assertEquals("no additional connectivity callback may drive the worker", 1, callbacks.get())
            waitForServiceToSettleAfterCompletion()
        } finally {
            context.stopService(serviceIntent)
            DownloadForegroundService.connectivityObserverFactoryForTesting = null
            server.close()
        }
    }

    @Test
    fun secondScheduleDuringActiveTransferDispatchesAfterFirstWorkerWithoutNewNetworkCallback() {
        deleteRuntimeState()
        val firstJobId = "rmd-1506-observer-active-first"
        val secondJobId = "rmd-1506-observer-active-second"
        val firstPath = "items/rmd-1506-observer-active-first/video.mp4"
        val secondPath = "items/rmd-1506-observer-active-second/video.mp4"
        // The existing fixture server intentionally throttles each 4 KiB chunk, so the
        // second schedule arrives while the first real generated-core worker is active.
        val firstPayload = ByteArray(2 * 1024 * 1024) { index -> (index % 251).toByte() }
        val secondPayload = ByteArray(16 * 1024) { index -> (index % 239).toByte() }
        val firstServer = SlowRangeFixtureServer(firstPayload).also { it.start() }
        val secondServer = SlowRangeFixtureServer(secondPayload).also { it.start() }
        val registrations = AtomicInteger(0)
        val callbacks = AtomicInteger(0)
        DownloadForegroundService.connectivityObserverFactoryForTesting = { _, onChanged ->
            registrations.incrementAndGet()
            callbacks.incrementAndGet()
            onChanged(DownloadConnectivity.Unmetered)
            Closeable { }
        }
        try {
            GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { controls ->
                for (jobId in listOf(firstJobId, secondJobId)) {
                    val result = controls.enqueue(jobId)
                    assertNull(result.error)
                    assertEquals(true, result.value)
                }
            }
            seedDurableWork(firstJobId, firstServer.mediaUrl, firstPath, firstPayload.size)
            seedDurableWork(secondJobId, secondServer.mediaUrl, secondPath, secondPayload.size)

            val scheduler = AndroidDownloadExecutionScheduler(context)
            assertTrue(scheduler.schedule(DownloadScheduleRequest(firstJobId)).accepted)
            waitForState(firstJobId, CoreDownloadState.DOWNLOADING)
            assertEquals(1, registrations.get())
            assertEquals(1, callbacks.get())

            // This intent reuses the already-registered observer; it cannot start its worker
            // until the first worker exits, and the observer deliberately emits no new event.
            assertTrue(scheduler.schedule(DownloadScheduleRequest(secondJobId)).accepted)
            waitForState(firstJobId, CoreDownloadState.COMPLETED)
            waitForState(secondJobId, CoreDownloadState.COMPLETED)
            assertEquals(firstPayload.size.toLong(), File(context.filesDir, firstPath).length())
            assertEquals(secondPayload.toList(), File(context.filesDir, secondPath).readBytes().toList())
            assertEquals("observer must not be registered twice", 1, registrations.get())
            assertEquals("no second network callback may be needed", 1, callbacks.get())
            waitForServiceToSettleAfterCompletion()
        } finally {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
            DownloadForegroundService.connectivityObserverFactoryForTesting = null
            firstServer.close()
            secondServer.close()
        }
    }

    private fun waitForServiceNotification() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (manager.activeNotifications.any { it.id == DownloadServicePolicy.NotificationId }) return
            SystemClock.sleep(50)
        }
        throw AssertionError("foreground service did not register its notification")
    }

    private fun waitForServiceToSettleAfterCompletion() {
        // A COMPLETED durable snapshot can be observed before the foreground
        // worker's finally/settlement code has finished reopening SQLite.
        // Do not erase the DB on test teardown until its notification is gone.
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (manager.activeNotifications.none { it.id == DownloadServicePolicy.NotificationId }) return
            SystemClock.sleep(50L)
        }
        throw AssertionError("foreground worker did not settle after completing fixture download")
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
                "media_id": "$jobId",
                "canonical_url": "https://fixture.invalid/$jobId"
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

    private fun waitForState(
        jobId: String,
        expected: CoreDownloadState,
        timeoutMs: Long = 30_000L,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var last: CoreDownloadState? = null
        // Keep one reader open while the worker updates the durable SQLite queue.
        // Reopening the Rust service for every 50-ms poll reruns schema initialization.
        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            while (SystemClock.elapsedRealtime() < deadline) {
                val queue = core.listDownloadQueue()
                assertNull(queue.error)
                val snapshot = queue.value?.firstOrNull { it.jobId == jobId }
                last = snapshot?.state
                if (last == expected) return
                if (last == CoreDownloadState.FAILED || last == CoreDownloadState.CANCELED) {
                    throw AssertionError(
                        "job $jobId entered terminal state $last while waiting for $expected; " +
                            "lastError=${snapshot?.lastError}",
                    )
                }
                SystemClock.sleep(50)
            }
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
