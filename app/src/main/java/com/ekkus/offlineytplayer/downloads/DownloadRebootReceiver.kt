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
    const val ReconciliationPath = "MainActivity.bootstrapProductionUi -> GeneratedUniffiCoreGateway.reconcileStartup"

    fun onReceive(action: String?): DownloadBootRecoveryDecision =
        if (action == Intent.ACTION_BOOT_COMPLETED) onBootCompleted() else ignored()

    fun onBootCompleted(): DownloadBootRecoveryDecision = DownloadBootRecoveryDecision(
        disposition = DownloadBootRecoveryDisposition.DeferUntilAppStartup,
        receiverHandled = true,
        startsForegroundService = false,
        opensCredentialProtectedStorage = false,
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
