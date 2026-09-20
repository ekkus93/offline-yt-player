package com.ekkus.offlineytplayer.coregateway

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
