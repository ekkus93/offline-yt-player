package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem

internal fun loadLibraryScreenState(gateway: AppCoreGateway): LibraryScreenState {
    val result = gateway.listLibrary()
    result.error?.let { return LibraryScreenState.Failed(it.message) }
    return LibraryScreenState.Ready(result.value.orEmpty().map(::libraryRow))
}

internal fun loadDownloadsScreenState(gateway: AppCoreGateway): DownloadsScreenState {
    val result = gateway.listDownloadQueue()
    result.error?.let { return DownloadsScreenState.Failed(it.message) }
    return DownloadsScreenState.Ready(result.value.orEmpty().map(::downloadRow))
}

private fun libraryRow(item: CoreLibraryItem): LibraryRowModel = LibraryRowModel(
    id = item.itemId,
    title = item.displayTitle,
    detail = buildString {
        append(item.qualityLabel)
        item.durationMs?.let { append(" · ${formatDuration(it)}") }
    },
    completed = item.completed,
    resumePositionMs = item.playbackPositionMs,
)

private fun downloadRow(item: CoreDownloadSnapshot): DownloadRowModel {
    val percent = item.totalBytes
        ?.takeIf { it > 0 }
        ?.let { total -> ((item.bytesDownloaded.coerceAtMost(total) * 100) / total).toInt() }
        ?: 0
    return DownloadRowModel(
        id = item.jobId,
        title = item.jobId,
        state = when (item.state) {
            CoreDownloadState.PAUSED -> DownloadUiState.Paused
            CoreDownloadState.FAILED -> DownloadUiState.Failed
            CoreDownloadState.COMPLETED -> DownloadUiState.Completed
            else -> DownloadUiState.Active
        },
        percent = percent,
        size = item.totalBytes?.let { "${item.bytesDownloaded} / $it bytes" } ?: "${item.bytesDownloaded} bytes",
        error = item.lastError?.message,
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
