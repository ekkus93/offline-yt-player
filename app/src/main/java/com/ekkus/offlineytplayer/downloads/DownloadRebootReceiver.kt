package com.ekkus.offlineytplayer.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Boot is only a durable-state signal. It must never directly start a dataSync
 * foreground service: target SDK 35+ forbids that launch from BOOT_COMPLETED.
 *
 * The production scheduler/repository introduced by RMD-103/RMD-500 owns
 * reconciliation and future eligible execution. Until that durable scheduler
 * exists, boot deliberately performs no process-local or credential-protected
 * work.
 */
class DownloadRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        DownloadBootRecovery.onBootCompleted()
    }
}

internal object DownloadBootRecovery {
    /**
     * True documents the platform invariant enforced by this receiver.
     * Scheduling is wired to durable queue state by RMD-103/RMD-500 rather than
     * starting DownloadForegroundService from a boot broadcast.
     */
    const val StartsForegroundServiceFromBoot = false
    const val UsesLockedBootCompleted = false

    fun onBootCompleted() {
        // Intentionally no credential-encrypted DB/media access here.
        // Durable reconciliation/scheduling is attached by RMD-103/RMD-500.
    }
}
