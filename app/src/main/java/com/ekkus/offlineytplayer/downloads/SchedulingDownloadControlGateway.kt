package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot

internal class SchedulingDownloadControlGateway(
    private val delegate: AppDownloadControlGateway,
    private val scheduler: DownloadExecutionScheduler,
    private val settingsSnapshot: () -> AppSettingsSnapshot,
) : AppDownloadControlGateway {
    override fun enqueue(jobId: String): CoreGatewayResult<Boolean> {
        val result = delegate.enqueue(jobId)
        if (result.error != null || result.value != true) return result

        val settings = settingsSnapshot()
        val scheduleResult = scheduler.schedule(
            DownloadScheduleRequest(
                queueItemId = jobId,
                estimatedDownloadBytes = null,
                networkPreference = if (settings.wifiOnlyDownloads) {
                    DownloadNetworkPreference.WifiOnly
                } else {
                    DownloadNetworkPreference.AnyNetwork
                },
            ),
        )
        if (scheduleResult.accepted) return result

        return CoreGatewayResult(
            value = false,
            error = CoreGatewayError(
                kind = "scheduler_rejected",
                message = "Download was queued but the Android runtime scheduler rejected it",
                retryable = true,
            ),
        )
    }

    override fun pause(jobId: String): CoreGatewayResult<Boolean> = delegate.pause(jobId)

    override fun resume(jobId: String): CoreGatewayResult<Boolean> = delegate.resume(jobId)

    override fun cancel(jobId: String): CoreGatewayResult<Boolean> = delegate.cancel(jobId)

    override fun retry(jobId: String): CoreGatewayResult<Boolean> = delegate.retry(jobId)

    override fun close() = delegate.close()
}
