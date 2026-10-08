package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import com.ekkus.offlineytplayer.coregateway.CoreSourceIdentity
import com.ekkus.offlineytplayer.coregateway.FakeCoreGateway
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
    fun refresherForwardsObservedLibraryQueryToRepository() {
        val gateway = FakeCoreGateway(
            initialItems = listOf(
                CoreLibraryItem("alpha", CoreSourceIdentity("fixture", "alpha", null), "Alpha", 1_000, "720p", 1, 0, true),
                CoreLibraryItem("beta", CoreSourceIdentity("fixture", "beta", null), "Beta Lecture", 1_000, "720p", 2, 0, true),
            ),
        )
        val latch = CountDownLatch(1)
        var observedIds = emptyList<String>()
        AppStateRefresher(
            gateway = gateway,
            onLibrary = { result -> observedIds = result.value.orEmpty().map { it.itemId }; latch.countDown() },
            onDownloads = {},
            intervalMs = 10_000,
            libraryQuery = { "lecture" },
        ).use { refresher ->
            refresher.start()
            assertTrue(latch.await(2, TimeUnit.SECONDS))
        }
        assertEquals(listOf("beta"), observedIds)
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

    @Test
    fun lifecycleRestartReobservesLatestQueryOffTheCallingThread() {
        val gateway = FakeCoreGateway(
            initialItems = listOf(
                CoreLibraryItem("alpha", CoreSourceIdentity("fixture", "alpha", null), "Alpha", 1_000, "720p", 1, 0, true),
                CoreLibraryItem("beta", CoreSourceIdentity("fixture", "beta", null), "Beta", 1_000, "720p", 2, 0, true),
            ),
        )
        val query = AtomicReference("alpha")
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val observedThreads = java.util.Collections.synchronizedList(mutableListOf<String>())
        val callerThread = Thread.currentThread().name
        AppStateRefresher(
            gateway = gateway,
            onLibrary = { result ->
                observedThreads.add(Thread.currentThread().name)
                when (result.value.orEmpty().map { it.itemId }) {
                    listOf("alpha") -> first.countDown()
                    listOf("beta") -> second.countDown()
                }
            },
            onDownloads = {},
            intervalMs = 100L,
            libraryQuery = { query.get() },
        ).use { refresher ->
            refresher.start()
            assertTrue(first.await(2, TimeUnit.SECONDS))
            refresher.stop()
            query.set("beta")
            refresher.start()
            assertTrue(second.await(2, TimeUnit.SECONDS))
            refresher.stop()
        }
        assertTrue(observedThreads.isNotEmpty())
        assertTrue(observedThreads.all { it != callerThread })
    }
}
