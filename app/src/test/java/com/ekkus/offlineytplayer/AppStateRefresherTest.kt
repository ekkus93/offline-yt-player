package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
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
    fun queryChangeDuringBlockingReadDoesNotPublishStaleLibraryResults() {
        val base = FakeCoreGateway(
            initialItems = listOf(
                CoreLibraryItem("alpha", CoreSourceIdentity("fixture", "alpha", null), "Alpha", 1_000, "720p", 1, 0, true),
                CoreLibraryItem("beta", CoreSourceIdentity("fixture", "beta", null), "Beta", 1_000, "720p", 2, 0, true),
            ),
        )
        val activeQuery = AtomicReference("alpha")
        val firstReadEntered = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        val freshObserved = CountDownLatch(1)
        val stalePublications = AtomicInteger()
        val gateway = object : AppCoreGateway by base {
            override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> {
                if (query == "alpha") {
                    firstReadEntered.countDown()
                    releaseFirstRead.await(2, TimeUnit.SECONDS)
                }
                return base.listLibrary(query)
            }
        }
        AppStateRefresher(
            gateway = gateway,
            onLibrary = { result ->
                when (result.value.orEmpty().map { it.itemId }) {
                    listOf("alpha") -> stalePublications.incrementAndGet()
                    listOf("beta") -> freshObserved.countDown()
                }
            },
            onDownloads = {},
            intervalMs = 25L,
            libraryQuery = { activeQuery.get() },
        ).use { refresher ->
            refresher.start()
            assertTrue(firstReadEntered.await(2, TimeUnit.SECONDS))
            activeQuery.set("beta")
            releaseFirstRead.countDown()
            assertTrue(freshObserved.await(2, TimeUnit.SECONDS))
        }
        assertEquals(0, stalePublications.get())
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
    @Test
    fun stoppedLifecycleSuppressesLateRepositoryResultAndRestartObservesFreshState() {
        val base = FakeCoreGateway()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstReturned = CountDownLatch(1)
        val freshObserved = CountDownLatch(1)
        val calls = AtomicInteger()
        val stalePublications = AtomicInteger()
        val gateway = object : AppCoreGateway by base {
            override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> {
                val isFirst = calls.incrementAndGet() == 1
                if (isFirst) {
                    firstEntered.countDown()
                    while (true) {
                        try {
                            releaseFirst.await()
                            break
                        } catch (_: InterruptedException) {
                            // Simulate a repository call that completes after lifecycle stop.
                        }
                    }
                    firstReturned.countDown()
                }
                val id = if (isFirst) "stale" else "fresh"
                return CoreGatewayResult(
                    listOf(CoreLibraryItem(id, CoreSourceIdentity("fixture", id, null), id, 1_000, "720p", 1, 0, true)),
                    null,
                )
            }
        }
        AppStateRefresher(
            gateway,
            onLibrary = { result ->
                when (result.value.orEmpty().firstOrNull()?.itemId) {
                    "stale" -> stalePublications.incrementAndGet()
                    "fresh" -> freshObserved.countDown()
                }
            },
            onDownloads = {},
            intervalMs = 25L,
        ).use { refresher ->
            refresher.start()
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            refresher.stop()
            releaseFirst.countDown()
            assertTrue(firstReturned.await(2, TimeUnit.SECONDS))
            refresher.start()
            assertTrue(freshObserved.await(2, TimeUnit.SECONDS))
            refresher.stop()
        }
        assertEquals(0, stalePublications.get())
    }

    @Test
    fun transientRepositoryFailuresPublishSanitizedErrorsAndRecoverOnNextPoll() {
        val base = FakeCoreGateway()
        val libraryCalls = AtomicInteger()
        val downloadCalls = AtomicInteger()
        val libraryFailed = CountDownLatch(1)
        val libraryRecovered = CountDownLatch(1)
        val downloadsFailed = CountDownLatch(1)
        val downloadsRecovered = CountDownLatch(1)
        val libraryDiagnostic = AtomicReference<String?>()
        val downloadsDiagnostic = AtomicReference<String?>()
        val gateway = object : AppCoreGateway by base {
            override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> {
                if (libraryCalls.incrementAndGet() == 1) throw IllegalStateException("private library URL")
                return base.listLibrary(query)
            }
            override fun listDownloadQueue(): CoreGatewayResult<List<CoreDownloadSnapshot>> {
                if (downloadCalls.incrementAndGet() == 1) throw IllegalStateException("private download path")
                return base.listDownloadQueue()
            }
        }
        AppStateRefresher(
            gateway = gateway,
            onLibrary = { result ->
                if (result.error != null) {
                    libraryDiagnostic.set(result.error.message)
                    libraryFailed.countDown()
                } else libraryRecovered.countDown()
            },
            onDownloads = { result ->
                if (result.error != null) {
                    downloadsDiagnostic.set(result.error.message)
                    downloadsFailed.countDown()
                } else downloadsRecovered.countDown()
            },
            intervalMs = 25L,
        ).use { refresher ->
            refresher.start()
            assertTrue(libraryFailed.await(2, TimeUnit.SECONDS))
            assertTrue(downloadsFailed.await(2, TimeUnit.SECONDS))
            assertTrue(libraryRecovered.await(2, TimeUnit.SECONDS))
            assertTrue(downloadsRecovered.await(2, TimeUnit.SECONDS))
        }
        assertEquals("Library refresh failed", libraryDiagnostic.get())
        assertEquals("Downloads refresh failed", downloadsDiagnostic.get())
    }

    @Test
    fun transientPublicationCallbackFailureDoesNotDisableFutureRefreshes() {
        val calls = AtomicInteger()
        val recovered = CountDownLatch(1)
        AppStateRefresher(
            gateway = FakeCoreGateway(),
            onLibrary = {
                if (calls.incrementAndGet() == 1) throw IllegalStateException("transient UI callback")
                recovered.countDown()
            },
            onDownloads = {},
            intervalMs = 25L,
        ).use { refresher ->
            refresher.start()
            assertTrue(recovered.await(2, TimeUnit.SECONDS))
        }
        assertTrue(calls.get() >= 2)
    }

}
