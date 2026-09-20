package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import com.ekkus.offlineytplayer.coregateway.CoreSourceIdentity
import com.ekkus.offlineytplayer.coregateway.FakeCoreGateway
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateRefresherTest {
    @Test
    fun startedRefresherPublishesDurableLibraryAndDownloadState() {
        val item = CoreLibraryItem("item-1", CoreSourceIdentity("fixture", "media-1", null), "Saved video", 1_000, "720p", 1, 0, true)
        val job = CoreDownloadSnapshot("job-1", CoreDownloadState.DOWNLOADING, 25, 100, 1, null, null)
        val gateway = FakeCoreGateway(listOf(item), listOf(job))
        val latch = CountDownLatch(2)
        val libraryCount = AtomicInteger()
        val downloadCount = AtomicInteger()
        AppStateRefresher(
            gateway,
            onLibrary = { result -> libraryCount.set(result.value.orEmpty().size); latch.countDown() },
            onDownloads = { result -> downloadCount.set(result.value.orEmpty().size); latch.countDown() },
            intervalMs = 10_000,
        ).use { refresher ->
            refresher.start()
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            refresher.stop()
        }
        assertEquals(1, libraryCount.get())
        assertEquals(1, downloadCount.get())
    }

    @Test
    fun stopIsIdempotentAndPreventsRestartDuplication() {
        val gateway = FakeCoreGateway()
        val callbacks = AtomicInteger()
        val first = CountDownLatch(1)
        AppStateRefresher(gateway, { callbacks.incrementAndGet(); first.countDown() }, { }, 10_000).use { refresher ->
            refresher.start()
            refresher.start()
            assertTrue(first.await(2, TimeUnit.SECONDS))
            refresher.stop()
            refresher.stop()
        }
        assertEquals(1, callbacks.get())
    }
}
