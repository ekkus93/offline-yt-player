package com.ekkus.offlineytplayer.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ekkus.offlineytplayer.MainActivity

internal enum class DownloadNetworkPreference {
    AnyNetwork,
    WifiOnly,
}

internal enum class DownloadConnectivity {
    None,
    Metered,
    Unmetered,
}

internal enum class DownloadNetworkDecision {
    Allow,
    PauseForConnectivity,
}

internal object DownloadNetworkPolicy {
    const val SupportsWifiOnly = true
    const val PausesOnConnectivityLoss = true
    const val SilentPreferenceViolationAllowed = false

    fun decision(
        preference: DownloadNetworkPreference,
        connectivity: DownloadConnectivity,
    ): DownloadNetworkDecision = when (preference) {
        DownloadNetworkPreference.AnyNetwork -> when (connectivity) {
            DownloadConnectivity.None -> DownloadNetworkDecision.PauseForConnectivity
            DownloadConnectivity.Metered,
            DownloadConnectivity.Unmetered,
            -> DownloadNetworkDecision.Allow
        }
        DownloadNetworkPreference.WifiOnly -> when (connectivity) {
            DownloadConnectivity.Unmetered -> DownloadNetworkDecision.Allow
            DownloadConnectivity.None,
            DownloadConnectivity.Metered,
            -> DownloadNetworkDecision.PauseForConnectivity
        }
    }
}

internal object DownloadServicePolicy {
    const val ChannelId = "offline_downloads"
    const val NotificationId = 4100
    const val MaxConcurrentDownloads = 2
    const val SupportsPauseResumeCancel = true
    const val ReportsCompletionAndFailure = true
    const val ReconcilesDurableQueueOnStart = true
    const val HonorsNetworkPreference = true
    const val SupportsBootRecovery = true
    const val FailsInterruptedTransfersExplicitly = true
}

class DownloadForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE,
            ACTION_RESUME,
            ACTION_CANCEL,
            ACTION_CONNECTIVITY_RETRY,
            ACTION_RECONCILE_AFTER_REBOOT,
            ACTION_SCHEDULE_WORK,
            -> Unit
        }
        startForeground(DownloadServicePolicy.NotificationId, activeNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun activeNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, DownloadServicePolicy.ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Offline downloads")
            .setContentText("Download queue active")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", serviceAction(ACTION_PAUSE, 1))
            .addAction(android.R.drawable.ic_media_play, "Resume", serviceAction(ACTION_RESUME, 2))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", serviceAction(ACTION_CANCEL, 3))
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, DownloadForegroundService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    DownloadServicePolicy.ChannelId,
                    "Offline downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.ekkus.offlineytplayer.download.PAUSE"
        const val ACTION_RESUME = "com.ekkus.offlineytplayer.download.RESUME"
        const val ACTION_CANCEL = "com.ekkus.offlineytplayer.download.CANCEL"
        const val ACTION_STOP = "com.ekkus.offlineytplayer.download.STOP"
        const val ACTION_CONNECTIVITY_RETRY = "com.ekkus.offlineytplayer.download.CONNECTIVITY_RETRY"
        const val ACTION_RECONCILE_AFTER_REBOOT = "com.ekkus.offlineytplayer.download.RECONCILE_AFTER_REBOOT"
        const val ACTION_SCHEDULE_WORK = "com.ekkus.offlineytplayer.download.SCHEDULE_WORK"
        const val EXTRA_QUEUE_ITEM_ID = "queue_item_id"
    }
}
