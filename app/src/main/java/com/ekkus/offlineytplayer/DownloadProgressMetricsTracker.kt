package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState

/** Transient estimates from actual durable queue counters, never test fixture labels. */
internal data class DownloadLiveMetrics(val bytesPerSecond: Long, val etaSeconds: Long?)

/**
 * The Rust queue exposes persisted bytes/state, but not a durable network rate.
 * Derive an instantaneous rate from successive positive, monotonic samples.
 * First observations, stopped/paused work, stalls, replaced attempts, and
 * invalid counters deliberately show "unavailable" rather than inventing data.
 */
internal class DownloadProgressMetricsTracker {
    private data class Sample(val bytes: Long, val attempt: Int, val elapsedMs: Long)
    private val samples = mutableMapOf<String, Sample>()

    @Synchronized
    fun clear() = samples.clear()

    @Synchronized
    fun observe(
        snapshots: List<CoreDownloadSnapshot>,
        elapsedRealtimeMs: Long,
    ): Map<String, DownloadLiveMetrics> {
        samples.keys.retainAll(snapshots.map { it.jobId }.toSet())
        val metrics = mutableMapOf<String, DownloadLiveMetrics>()
        for (row in snapshots) {
            if (row.jobId.isBlank() || row.state != CoreDownloadState.DOWNLOADING ||
                row.bytesDownloaded < 0L || elapsedRealtimeMs < 0L
            ) {
                samples.remove(row.jobId)
                continue
            }
            val before = samples[row.jobId]
            val current = Sample(row.bytesDownloaded, row.attempt, elapsedRealtimeMs)
            if (before == null || before.attempt != row.attempt ||
                row.bytesDownloaded < before.bytes || elapsedRealtimeMs <= before.elapsedMs
            ) {
                samples[row.jobId] = current
                continue
            }
            val elapsed = elapsedRealtimeMs - before.elapsedMs
            if (elapsed < 500L) continue
            samples[row.jobId] = current
            val increment = row.bytesDownloaded - before.bytes
            if (increment <= 0L) continue
            val speed = (increment.toDouble() * 1_000.0 / elapsed).toLong()
            if (speed <= 0L) continue
            val remaining = row.totalBytes?.takeIf { it > row.bytesDownloaded }?.let { total ->
                kotlin.math.ceil((total - row.bytesDownloaded).toDouble() / speed).toLong()
            }
            metrics[row.jobId] = DownloadLiveMetrics(speed, remaining)
        }
        return metrics
    }
}

internal fun formatDownloadSpeed(metrics: DownloadLiveMetrics?): String {
    val speed = metrics?.bytesPerSecond?.takeIf { it > 0L } ?: return "Speed unavailable"
    return when {
        speed >= 1024L * 1024L -> "${speed / (1024L * 1024L)} MiB/s"
        speed >= 1024L -> "${speed / 1024L} KiB/s"
        else -> "$speed B/s"
    }
}

internal fun formatDownloadEta(metrics: DownloadLiveMetrics?): String {
    val seconds = metrics?.etaSeconds?.takeIf { it >= 0L } ?: return "ETA unavailable"
    return if (seconds >= 60L) "ETA ${seconds / 60L}m ${seconds % 60L}s"
    else "ETA ${seconds}s"
}
