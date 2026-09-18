package com.ekkus.offlineytplayer.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ekkus.offlineytplayer.MainActivity
import kotlin.math.absoluteValue

internal enum class DownloadSchedulerKind {
    UserInitiatedDataTransferJob,
    ForegroundServiceFallback,
}

internal data class DownloadScheduleRequest(
    val queueItemId: String,
    val estimatedDownloadBytes: Long? = null,
    val networkPreference: DownloadNetworkPreference = DownloadNetworkPreference.AnyNetwork,
)

internal data class DownloadScheduleResult(
    val kind: DownloadSchedulerKind,
    val accepted: Boolean,
    val jobId: Int? = null,
)

internal interface DownloadExecutionScheduler {
    fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult
}

internal object DownloadExecutionSchedulerPolicy {
    const val UserInitiatedDataTransferMinSdk = 34
    const val UsesSharedDurableQueue = true
    const val UidtUpdatesNotification = true

    fun schedulerKindForSdk(sdkInt: Int): DownloadSchedulerKind =
        if (sdkInt >= UserInitiatedDataTransferMinSdk) {
            DownloadSchedulerKind.UserInitiatedDataTransferJob
        } else {
            DownloadSchedulerKind.ForegroundServiceFallback
        }

    fun requiredNetworkType(preference: DownloadNetworkPreference): Int = when (preference) {
        DownloadNetworkPreference.AnyNetwork -> JobInfo.NETWORK_TYPE_ANY
        DownloadNetworkPreference.WifiOnly -> JobInfo.NETWORK_TYPE_UNMETERED
    }

    fun stableJobId(queueItemId: String): Int =
        DownloadUserInitiatedJobService.JobIdBase + (queueItemId.hashCode().absoluteValue % DownloadUserInitiatedJobService.JobIdRange)
}

internal class AndroidDownloadExecutionScheduler(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val jobScheduler: JobScheduler? = context.getSystemService(JobScheduler::class.java),
) : DownloadExecutionScheduler {
    override fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult =
        when (DownloadExecutionSchedulerPolicy.schedulerKindForSdk(sdkInt)) {
            DownloadSchedulerKind.UserInitiatedDataTransferJob -> scheduleUserInitiatedJob(request)
            DownloadSchedulerKind.ForegroundServiceFallback -> startForegroundFallback(request)
        }

    private fun scheduleUserInitiatedJob(request: DownloadScheduleRequest): DownloadScheduleResult {
        val jobId = DownloadExecutionSchedulerPolicy.stableJobId(request.queueItemId)
        val builder = JobInfo.Builder(
            jobId,
            ComponentName(context, DownloadUserInitiatedJobService::class.java),
        )
            .setExtras(
                PersistableBundle().apply {
                    putString(DownloadUserInitiatedJobService.ExtraQueueItemId, request.queueItemId)
                },
            )
            .setRequiredNetworkType(DownloadExecutionSchedulerPolicy.requiredNetworkType(request.networkPreference))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setEstimatedNetworkBytes(
                request.estimatedDownloadBytes ?: JobInfo.NETWORK_BYTES_UNKNOWN.toLong(),
                JobInfo.NETWORK_BYTES_UNKNOWN.toLong(),
            )
        }
        if (Build.VERSION.SDK_INT >= DownloadExecutionSchedulerPolicy.UserInitiatedDataTransferMinSdk) {
            builder.setUserInitiated(true)
        }

        val result = jobScheduler?.schedule(builder.build()) ?: JobScheduler.RESULT_FAILURE
        return DownloadScheduleResult(
            kind = DownloadSchedulerKind.UserInitiatedDataTransferJob,
            accepted = result == JobScheduler.RESULT_SUCCESS,
            jobId = jobId,
        )
    }

    private fun startForegroundFallback(request: DownloadScheduleRequest): DownloadScheduleResult {
        ContextCompat.startForegroundService(
            context,
            Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_SCHEDULE_WORK
                putExtra(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, request.queueItemId)
            },
        )
        return DownloadScheduleResult(
            kind = DownloadSchedulerKind.ForegroundServiceFallback,
            accepted = true,
            jobId = null,
        )
    }
}

class DownloadUserInitiatedJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        ensureNotificationChannel()
        if (Build.VERSION.SDK_INT >= DownloadExecutionSchedulerPolicy.UserInitiatedDataTransferMinSdk) {
            setNotification(
                params,
                DownloadServicePolicy.NotificationId,
                downloadExecutionNotification(),
                JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
        }
        // RMD-500 attaches the durable worker loop. This job is now the legal
        // API 34+ launch point and notification owner for user-requested work.
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    private fun downloadExecutionNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, DownloadServicePolicy.ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Offline downloads")
            .setContentText("Preparing queued download")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .build()
    }

    private fun ensureNotificationChannel() {
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
        const val JobIdBase = 7300
        const val JobIdRange = 10_000
        const val ExtraQueueItemId = "queue_item_id"
    }
}
