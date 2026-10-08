package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
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
            val library = readSafely("Library") { gateway.listLibrary(libraryQuery()) }
            if (epoch != lifecycleEpoch.get()) return
            try { onLibrary(library) } catch (_: RuntimeException) { /* Retry on the next poll. */ }
            val downloads = readSafely("Downloads") { gateway.listDownloadQueue() }
            if (epoch != lifecycleEpoch.get()) return
            try { onDownloads(downloads) } catch (_: RuntimeException) { /* Retry on the next poll. */ }
        } finally {
            refreshInFlight.set(false)
        }
    }

    private fun <T> readSafely(label: String, read: () -> CoreGatewayResult<T>): CoreGatewayResult<T> =
        try {
            read()
        } catch (_: RuntimeException) {
            // Do not let a transient FFI/repository exception permanently cancel periodic polling.
            // Never expose exception text, which may contain paths or source URLs, to the UI.
            CoreGatewayResult(null, CoreGatewayError("repository_unavailable", "$label refresh failed", true))
        }

    override fun close() = stop()
}
