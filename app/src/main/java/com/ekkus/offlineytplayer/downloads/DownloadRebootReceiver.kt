package com.ekkus.offlineytplayer.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boot is only a durable-state signal. It must never directly start a dataSync
 * foreground service: target SDK 35+ forbids that launch from BOOT_COMPLETED.
 *
 * The app uses the same startup reconciliation path as normal process relaunch
 * and waits until credential-protected app storage is available before reading
 * the queue database or media root. Receiver handling is therefore limited to a
 * legal, durable recovery decision rather than a service launch.
 */
class DownloadRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DownloadBootRecovery.onReceive(intent?.action)
    }
}

internal enum class DownloadBootRecoveryDisposition {
    Ignore,
    DeferUntilAppStartup,
}

internal data class DownloadBootRecoveryDecision(
    val disposition: DownloadBootRecoveryDisposition,
    val receiverHandled: Boolean,
    val startsForegroundService: Boolean,
    val opensCredentialProtectedStorage: Boolean,
    val workRemainsRecoverable: Boolean,
    val scheduledOnlyWhenLegal: Boolean,
    val reconciliationPath: String?,
)

internal object DownloadBootRecovery {
    /**
     * True documents the platform invariant enforced by this receiver.
     * Scheduling is wired to durable queue state by RMD-103/RMD-500 rather than
     * starting DownloadForegroundService from a boot broadcast.
     */
    const val StartsForegroundServiceFromBoot = false
    const val UsesLockedBootCompleted = false
    const val OpensCredentialProtectedStorageOnBoot = false
    const val ReconciliationPath = "MainActivity.bootstrapProductionUi -> GeneratedUniffiCoreGateway.reconcileStartup"

    fun onReceive(action: String?): DownloadBootRecoveryDecision =
        if (action == Intent.ACTION_BOOT_COMPLETED) onBootCompleted() else ignored()

    fun onBootCompleted(): DownloadBootRecoveryDecision = DownloadBootRecoveryDecision(
        disposition = DownloadBootRecoveryDisposition.DeferUntilAppStartup,
        receiverHandled = true,
        startsForegroundService = StartsForegroundServiceFromBoot,
        opensCredentialProtectedStorage = OpensCredentialProtectedStorageOnBoot,
        workRemainsRecoverable = true,
        scheduledOnlyWhenLegal = true,
        reconciliationPath = ReconciliationPath,
    )

    private fun ignored(): DownloadBootRecoveryDecision = DownloadBootRecoveryDecision(
        disposition = DownloadBootRecoveryDisposition.Ignore,
        receiverHandled = false,
        startsForegroundService = false,
        opensCredentialProtectedStorage = false,
        workRemainsRecoverable = false,
        scheduledOnlyWhenLegal = true,
        reconciliationPath = null,
    )
}
