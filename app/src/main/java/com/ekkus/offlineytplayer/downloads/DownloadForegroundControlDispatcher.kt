package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway

internal object DownloadForegroundControlDispatcher {
    fun dispatch(
        action: String?,
        queueItemId: String?,
        gateway: AppDownloadControlGateway,
    ): Boolean {
        val jobId = queueItemId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val result = when (action) {
            DownloadForegroundService.ACTION_PAUSE -> gateway.pause(jobId)
            DownloadForegroundService.ACTION_RESUME -> gateway.resume(jobId)
            DownloadForegroundService.ACTION_CANCEL -> gateway.cancel(jobId)
            else -> return false
        }
        return result.error == null && result.value == true
    }
}
