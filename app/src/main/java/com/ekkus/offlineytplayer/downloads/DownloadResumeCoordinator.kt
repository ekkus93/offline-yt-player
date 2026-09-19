package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError

internal enum class DownloadResumeDecision {
    Schedule,
    WaitForConnectivity,
}

internal enum class DownloadResumeStatus {
    Scheduled,
    WaitingForConnectivity,
    ControlRejected,
    ScheduleRejected,
}

internal data class DownloadResumeRequest(
    val queueItemId: String,
    val estimatedDownloadBytes: Long? = null,
    val networkPreference: DownloadNetworkPreference = DownloadNetworkPreference.AnyNetwork,
)

internal data class DownloadResumeResult(
    val status: DownloadResumeStatus,
    val controlUpdated: Boolean = false,
    val scheduleKind: DownloadSchedulerKind? = null,
    val error: CoreGatewayError? = null,
) {
    val accepted: Boolean get() = status == DownloadResumeStatus.Scheduled
}

internal object DownloadResumePolicy {
    const val ResumeTransitionsToEligibleQueueState = true
    const val ResumeDoesNotBypassScheduler = true
    const val ResumeHonorsNetworkPreference = true

    fun decision(
        preference: DownloadNetworkPreference,
        connectivity: DownloadConnectivity,
    ): DownloadResumeDecision = when (DownloadNetworkPolicy.decision(preference, connectivity)) {
        DownloadNetworkDecision.Allow -> DownloadResumeDecision.Schedule
        DownloadNetworkDecision.PauseForConnectivity -> DownloadResumeDecision.WaitForConnectivity
    }

    fun scheduleRequest(request: DownloadResumeRequest): DownloadScheduleRequest = DownloadScheduleRequest(
        queueItemId = request.queueItemId,
        estimatedDownloadBytes = request.estimatedDownloadBytes,
        networkPreference = request.networkPreference,
    )
}

internal class DownloadResumeCoordinator(
    private val controlGateway: AppDownloadControlGateway,
    private val scheduler: DownloadExecutionScheduler,
) {
    fun resume(
        request: DownloadResumeRequest,
        connectivity: DownloadConnectivity,
    ): DownloadResumeResult {
        if (DownloadResumePolicy.decision(request.networkPreference, connectivity) ==
            DownloadResumeDecision.WaitForConnectivity
        ) {
            return DownloadResumeResult(status = DownloadResumeStatus.WaitingForConnectivity)
        }

        val control = controlGateway.resume(request.queueItemId)
        if (!control.isSuccess) {
            return DownloadResumeResult(
                status = DownloadResumeStatus.ControlRejected,
                error = control.error,
            )
        }

        val scheduled = scheduler.schedule(DownloadResumePolicy.scheduleRequest(request))
        return if (scheduled.accepted) {
            DownloadResumeResult(
                status = DownloadResumeStatus.Scheduled,
                controlUpdated = control.value == true,
                scheduleKind = scheduled.kind,
            )
        } else {
            DownloadResumeResult(
                status = DownloadResumeStatus.ScheduleRejected,
                controlUpdated = control.value == true,
                scheduleKind = scheduled.kind,
            )
        }
    }
}
