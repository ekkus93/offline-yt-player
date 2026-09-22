package com.ekkus.offlineytplayer.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import java.io.File
import java.util.concurrent.Executors

/**
 * Boot is only a durable-state recovery signal. It never directly starts a
 * dataSync foreground service: target SDK 35+ forbids that launch from
 * BOOT_COMPLETED.
 *
 * BOOT_COMPLETED is delivered after credential-encrypted storage is available,
 * so the receiver may reconcile the app-private durable queue. It deliberately
 * does not schedule user-initiated work from the boot broadcast; recovered work
 * remains durable and is scheduled later from a legal user/runtime entry point.
 */
class DownloadRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!DownloadBootRecovery.shouldReconcile(intent?.action)) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        DownloadBootRecovery.execute {
            try {
                DownloadBootRecovery.reconcile(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal object DownloadBootRecovery {
    const val StartsForegroundServiceFromBoot = false
    const val SchedulesUserInitiatedJobFromBoot = false
    const val UsesLockedBootCompleted = false

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "offline-yt-boot-recovery").apply { isDaemon = true }
    }

    fun shouldReconcile(action: String?): Boolean = action == Intent.ACTION_BOOT_COMPLETED

    fun execute(block: () -> Unit) {
        executor.execute(block)
    }

    fun reconcile(context: Context) {
        val databasePath = File(context.filesDir, "offline-yt-player.sqlite3").absolutePath
        GeneratedUniffiCoreGateway.open(databasePath).use { gateway ->
            gateway.reconcileStartup()
        }
    }
}
