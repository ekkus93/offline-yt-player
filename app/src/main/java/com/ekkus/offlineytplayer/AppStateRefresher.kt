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

/** Lifecycle-controlled observable-state bridge for the blocking core repository. */
class AppStateRefresher(
    private val gateway: AppCoreGateway,
    private val onLibrary: (CoreGatewayResult<List<CoreLibraryItem>>) -> Unit,
    private val onDownloads: (CoreGatewayResult<List<CoreDownloadSnapshot>>) -> Unit,
    private val intervalMs: Long = 1_000L,
) : Closeable {
    private var executor: ScheduledExecutorService? = null
    private val refreshInFlight = AtomicBoolean(false)

    @Synchronized
    fun start() {
        if (executor != null) return
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "offline-yt-app-state").apply { isDaemon = true }
        }.also { scheduler ->
            scheduler.scheduleWithFixedDelay(::refresh, 0L, intervalMs, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    private fun refresh() {
        if (!refreshInFlight.compareAndSet(false, true)) return
        try {
            onLibrary(gateway.listLibrary())
            onDownloads(gateway.listDownloadQueue())
        } finally {
            refreshInFlight.set(false)
        }
    }

    override fun close() = stop()
}
