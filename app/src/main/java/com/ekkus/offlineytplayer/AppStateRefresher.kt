package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Lifecycle-controlled observable-state bridge for the blocking core repository. */
class AppStateRefresher(
    private val gateway: AppCoreGateway,
    private val onLibrary: (CoreGatewayResult<List<CoreLibraryItem>>) -> Unit,
    private val onDownloads: (CoreGatewayResult<List<CoreDownloadSnapshot>>) -> Unit,
    private val intervalMs: Long = 1_000L,
    private val libraryQuery: () -> String? = { null },
) : Closeable {
    private var executor: ScheduledExecutorService? = null
    private val refreshInFlight = AtomicBoolean(false)
    private val lifecycleEpoch = AtomicLong(0L)

    @Synchronized
    fun start() {
        if (executor != null) return
        val epoch = lifecycleEpoch.incrementAndGet()
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "offline-yt-app-state").apply { isDaemon = true }
        }.also { scheduler ->
            scheduler.scheduleWithFixedDelay({ refresh(epoch) }, 0L, intervalMs, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    fun stop() {
        lifecycleEpoch.incrementAndGet()
        executor?.shutdownNow()
        executor = null
    }

    private fun refresh(epoch: Long) {
        if (epoch != lifecycleEpoch.get() || !refreshInFlight.compareAndSet(false, true)) return
        try {
            val library = gateway.listLibrary(libraryQuery())
            if (epoch != lifecycleEpoch.get()) return
            onLibrary(library)
            val downloads = gateway.listDownloadQueue()
            if (epoch != lifecycleEpoch.get()) return
            onDownloads(downloads)
        } finally {
            refreshInFlight.set(false)
        }
    }

    override fun close() = stop()
}
