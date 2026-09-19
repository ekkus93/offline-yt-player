package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway

internal enum class DownloadRowAction { Pause, Resume, Cancel, Retry }

internal data class DownloadProgressMetrics(
    val speedBytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
)

internal data class DownloadRowPresentation(
    val state: DownloadVisualState,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val errorReason: String? = null,
    val speedBytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
) {
    val progressFraction: Double?
        get() = totalBytes?.takeIf { it > 0 }?.let { bytesDownloaded.coerceAtMost(it).toDouble() / it }

    val progressPercent: Int?
        get() = progressFraction?.let { (it * 100).toInt() }

    fun actions(): Set<DownloadRowAction> = when (state) {
        DownloadVisualState.Active -> setOf(DownloadRowAction.Pause, DownloadRowAction.Cancel)
        DownloadVisualState.Paused -> setOf(DownloadRowAction.Resume, DownloadRowAction.Cancel)
        DownloadVisualState.Failed -> setOf(DownloadRowAction.Retry, DownloadRowAction.Cancel)
        DownloadVisualState.Completed -> emptySet()
    }

    fun humanReadableError(): String? = errorReason?.trim()?.takeIf { it.isNotEmpty() }

    fun trustworthySpeedBytesPerSecond(): Long? = speedBytesPerSecond?.takeIf { it > 0 }

    fun trustworthyEtaSeconds(): Long? = etaSeconds
        ?.takeIf { it >= 0 }
        ?.takeIf { totalBytes != null && bytesDownloaded <= totalBytes }

    fun trustworthySpeedAndEta(): Pair<Long, Long>? {
        val speed = trustworthySpeedBytesPerSecond() ?: return null
        val eta = trustworthyEtaSeconds() ?: return null
        return speed to eta
    }
}

internal object DownloadProgressPresentationMapper {
    fun fromProgress(
        state: DownloadVisualState,
        bytesDownloaded: Long,
        totalBytes: Long?,
        metrics: DownloadProgressMetrics = DownloadProgressMetrics(),
        errorReason: String? = null,
    ): DownloadRowPresentation = DownloadRowPresentation(
        state = state,
        bytesDownloaded = bytesDownloaded.coerceAtLeast(0),
        totalBytes = totalBytes?.takeIf { it > 0 },
        errorReason = errorReason,
        speedBytesPerSecond = metrics.speedBytesPerSecond?.takeIf { it > 0 },
        etaSeconds = metrics.etaSeconds?.takeIf { eta -> eta >= 0 && totalBytes != null },
    )
}

internal object DownloadRowControlBinding {
    fun invoke(
        action: DownloadRowAction,
        jobId: String,
        gateway: AppDownloadControlGateway,
    ): Boolean = when (action) {
        DownloadRowAction.Pause -> gateway.pause(jobId).isSuccess
        DownloadRowAction.Resume -> gateway.resume(jobId).isSuccess
        DownloadRowAction.Cancel -> gateway.cancel(jobId).isSuccess
        DownloadRowAction.Retry -> gateway.retry(jobId).isSuccess
    }
}
