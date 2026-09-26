package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot

internal class SchedulingDownloadControlGateway(
    private val delegate: AppDownloadControlGateway,
    private val scheduler: DownloadExecutionScheduler,
    private val settingsSnapshot: () -> AppSettingsSnapshot,
    private val connectivitySnapshot: () -> DownloadConnectivity = { DownloadConnectivity.Unmetered },
) : AppDownloadControlGateway {
    override fun enqueue(jobId: String): CoreGatewayResult<Boolean> {
        val result = delegate.enqueue(jobId)
        if (result.error != null || result.value != true) return result

        val scheduleResult = scheduler.schedule(
            DownloadScheduleRequest(
                queueItemId = jobId,
                estimatedDownloadBytes = null,
                networkPreference = currentNetworkPreference(),
            ),
        )
        if (scheduleResult.accepted) return result

        return schedulerRejected("Download was queued but the Android runtime scheduler rejected it")
    }

    override fun pause(jobId: String): CoreGatewayResult<Boolean> = delegate.pause(jobId)

    override fun resume(jobId: String): CoreGatewayResult<Boolean> {
        val result = DownloadResumeCoordinator(delegate, scheduler).resume(
            request = DownloadResumeRequest(
                queueItemId = jobId,
                estimatedDownloadBytes = null,
                networkPreference = currentNetworkPreference(),
            ),
            connectivity = connectivitySnapshot(),
        )
        return when (result.status) {
            DownloadResumeStatus.Scheduled -> CoreGatewayResult(value = result.controlUpdated, error = null)
            DownloadResumeStatus.WaitingForConnectivity -> CoreGatewayResult(
                value = false,
                error = CoreGatewayError(
                    kind = "waiting_for_connectivity",
                    message = "Download remains paused until the current network satisfies download settings",
                    retryable = true,
                ),
            )
            DownloadResumeStatus.ControlRejected -> CoreGatewayResult(
                value = false,
                error = result.error ?: CoreGatewayError(
                    kind = "resume_rejected",
                    message = "Download resume was rejected by the durable queue",
                    retryable = true,
                ),
            )
            DownloadResumeStatus.ScheduleRejected -> schedulerRejected(
                "Download was resumed but the Android runtime scheduler rejected it",
            )
        }
    }

    override fun cancel(jobId: String): CoreGatewayResult<Boolean> = delegate.cancel(jobId)

    override fun retry(jobId: String): CoreGatewayResult<Boolean> = delegate.retry(jobId)

    override fun close() = delegate.close()

    private fun currentNetworkPreference(): DownloadNetworkPreference = if (settingsSnapshot().wifiOnlyDownloads) {
        DownloadNetworkPreference.WifiOnly
    } else {
        DownloadNetworkPreference.AnyNetwork
    }

    private fun schedulerRejected(message: String): CoreGatewayResult<Boolean> = CoreGatewayResult(
        value = false,
        error = CoreGatewayError(
            kind = "scheduler_rejected",
            message = message,
            retryable = true,
        ),
    )
}
