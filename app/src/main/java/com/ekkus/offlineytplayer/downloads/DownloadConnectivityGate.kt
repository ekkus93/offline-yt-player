package com.ekkus.offlineytplayer.downloads

import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError

internal data class DownloadConnectivityWorkItem(
    val queueItemId: String,
    val estimatedDownloadBytes: Long? = null,
    val networkPreference: DownloadNetworkPreference = DownloadNetworkPreference.AnyNetwork,
)

internal enum class DownloadConnectivityGateStatus {
    Allowed,
    PausedForConnectivity,
    StillWaiting,
    Rescheduled,
    ControlRejected,
    ScheduleRejected,
}

internal data class DownloadConnectivityGateResult(
    val queueItemId: String,
    val status: DownloadConnectivityGateStatus,
    val controlUpdated: Boolean = false,
    val scheduleKind: DownloadSchedulerKind? = null,
    val error: CoreGatewayError? = null,
) {
    val accepted: Boolean get() = status == DownloadConnectivityGateStatus.Allowed || status == DownloadConnectivityGateStatus.Rescheduled
}

internal object DownloadConnectivityGatePolicy {
    const val PausesActiveWorkWhenPolicyDisallows = true
    const val EnforcesWifiOnlyPreference = true
    const val RequeuesWaitingWorkWhenConstraintsReturn = true

    fun shouldPause(
        preference: DownloadNetworkPreference,
        connectivity: DownloadConnectivity,
    ): Boolean = DownloadNetworkPolicy.decision(preference, connectivity) == DownloadNetworkDecision.PauseForConnectivity

    fun scheduleRequest(work: DownloadConnectivityWorkItem): DownloadScheduleRequest = DownloadScheduleRequest(
        queueItemId = work.queueItemId,
        estimatedDownloadBytes = work.estimatedDownloadBytes,
        networkPreference = work.networkPreference,
    )
}

/**
 * Bridges the Android connectivity observer to the shared durable control/scheduler path. Active
 * work is paused through the same control gateway used by UI/notification actions, and waiting work
 * becomes scheduler-eligible only when the current network satisfies its original preference.
 */
internal class DownloadConnectivityGate(
    private val controlGateway: AppDownloadControlGateway,
    private val scheduler: DownloadExecutionScheduler,
) {
    private val waiting = linkedMapOf<String, DownloadConnectivityWorkItem>()

    fun reconcileActiveWork(
        work: DownloadConnectivityWorkItem,
        connectivity: DownloadConnectivity,
    ): DownloadConnectivityGateResult {
        if (!DownloadConnectivityGatePolicy.shouldPause(work.networkPreference, connectivity)) {
            waiting.remove(work.queueItemId)
            return DownloadConnectivityGateResult(
                queueItemId = work.queueItemId,
                status = DownloadConnectivityGateStatus.Allowed,
            )
        }

        val control = controlGateway.pause(work.queueItemId)
        if (!control.isSuccess) {
            return DownloadConnectivityGateResult(
                queueItemId = work.queueItemId,
                status = DownloadConnectivityGateStatus.ControlRejected,
                error = control.error,
            )
        }

        waiting[work.queueItemId] = work
        return DownloadConnectivityGateResult(
            queueItemId = work.queueItemId,
            status = DownloadConnectivityGateStatus.PausedForConnectivity,
            controlUpdated = control.value == true,
        )
    }

    fun onConnectivityChanged(connectivity: DownloadConnectivity): List<DownloadConnectivityGateResult> {
        if (waiting.isEmpty()) return emptyList()
        return waiting.values.toList().map { work ->
            if (DownloadConnectivityGatePolicy.shouldPause(work.networkPreference, connectivity)) {
                DownloadConnectivityGateResult(
                    queueItemId = work.queueItemId,
                    status = DownloadConnectivityGateStatus.StillWaiting,
                )
            } else {
                val scheduled = scheduler.schedule(DownloadConnectivityGatePolicy.scheduleRequest(work))
                if (scheduled.accepted) {
                    waiting.remove(work.queueItemId)
                    DownloadConnectivityGateResult(
                        queueItemId = work.queueItemId,
                        status = DownloadConnectivityGateStatus.Rescheduled,
                        scheduleKind = scheduled.kind,
                    )
                } else {
                    DownloadConnectivityGateResult(
                        queueItemId = work.queueItemId,
                        status = DownloadConnectivityGateStatus.ScheduleRejected,
                        scheduleKind = scheduled.kind,
                    )
                }
            }
        }
    }

    fun waitingQueueItemIds(): Set<String> = waiting.keys.toSet()
}
