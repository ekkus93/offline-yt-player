package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.DownloadSelectionOptions
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot

internal class SchedulingDownloadControlGateway(
    private val delegate: AppDownloadControlGateway,
    private val scheduler: DownloadExecutionScheduler,
    private val settingsSnapshot: () -> AppSettingsSnapshot,
) : AppDownloadControlGateway {
    override fun enqueue(jobId: String): CoreGatewayResult<Boolean> = enqueue(jobId, null)

    override fun enqueue(jobId: String, choiceId: String?): CoreGatewayResult<Boolean> =
        scheduleAfterMutation(jobId, delegate.enqueue(jobId, choiceId))

    override fun enqueue(jobId: String, options: DownloadSelectionOptions): CoreGatewayResult<Boolean> =
        scheduleAfterMutation(jobId, delegate.enqueue(jobId, options))

    private fun scheduleAfterMutation(
        jobId: String,
        result: CoreGatewayResult<Boolean>,
    ): CoreGatewayResult<Boolean> {
        if (result.error != null || result.value != true) return result

        val settings = settingsSnapshot()
        val scheduleResult = try {
            scheduler.schedule(
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
        } catch (_: RuntimeException) {
            null
        }
        if (scheduleResult?.accepted == true) return result

        return CoreGatewayResult(
            value = false,
            error = CoreGatewayError(
                kind = "scheduler_rejected",
                message = "Download state changed but Android runtime scheduling was rejected",
                retryable = true,
            ),
        )
    }

    override fun pause(jobId: String): CoreGatewayResult<Boolean> = delegate.pause(jobId)

    override fun resume(jobId: String): CoreGatewayResult<Boolean> =
        scheduleAfterMutation(jobId, delegate.resume(jobId))

    override fun cancel(jobId: String): CoreGatewayResult<Boolean> = delegate.cancel(jobId)

    override fun retry(jobId: String): CoreGatewayResult<Boolean> =
        scheduleAfterMutation(jobId, delegate.retry(jobId))

    override fun close() = delegate.close()
}
