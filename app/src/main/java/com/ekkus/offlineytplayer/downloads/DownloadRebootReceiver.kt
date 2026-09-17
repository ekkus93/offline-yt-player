package com.ekkus.offlineytplayer.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class DownloadRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadForegroundService::class.java)
                    .setAction(DownloadForegroundService.ACTION_RECONCILE_AFTER_REBOOT),
            )
        }
    }
}
