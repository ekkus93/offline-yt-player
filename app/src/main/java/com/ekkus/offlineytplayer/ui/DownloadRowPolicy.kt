package com.ekkus.offlineytplayer.ui

internal enum class DownloadRowAction { Pause, Resume, Cancel, Retry }

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

    fun trustworthySpeedAndEta(): Pair<Long, Long>? {
        val speed = speedBytesPerSecond?.takeIf { it > 0 } ?: return null
        val eta = etaSeconds?.takeIf { it >= 0 } ?: return null
        return speed to eta
    }
}
