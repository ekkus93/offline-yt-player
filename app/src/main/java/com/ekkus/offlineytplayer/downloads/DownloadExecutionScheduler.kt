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
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ekkus.offlineytplayer.MainActivity
import kotlin.math.absoluteValue
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    fun schedulerKindForSdk(sdkInt: Int): DownloadSchedulerKind =
        if (sdkInt >= UserInitiatedDataTransferMinSdk) {
            DownloadSchedulerKind.UserInitiatedDataTransferJob
        } else {
            DownloadSchedulerKind.ForegroundServiceFallback
        }

    fun supportsEstimatedNetworkBytes(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.P

    /** JobInfo accepts only nonnegative byte estimates or NETWORK_BYTES_UNKNOWN (-1). */
    fun normalizedEstimatedNetworkBytes(estimate: Long?): Long =
        estimate?.takeIf { it >= 0L } ?: JobInfo.NETWORK_BYTES_UNKNOWN.toLong()

    fun supportsUserInitiatedJobFlag(sdkInt: Int): Boolean = sdkInt >= UserInitiatedDataTransferMinSdk

    /** An unmetered Ethernet/cellular network is not a Wi-Fi connection. */
    fun applyNetworkConstraint(
        builder: JobInfo.Builder,
        preference: DownloadNetworkPreference,
    ): JobInfo.Builder = when (preference) {
        DownloadNetworkPreference.AnyNetwork ->
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        DownloadNetworkPreference.WifiOnly -> {
            // UIDT runs on API 34+, but this helper is compiled for minSdk 26.
            // Guard the API-28 JobInfo overload explicitly for Android lint.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                throw IllegalStateException("Wi-Fi-only UIDT requires API 28 or newer")
            }
            builder.setRequiredNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    .build(),
            )
        }
    }

    fun usesForegroundFallback(sdkInt: Int): Boolean =
        schedulerKindForSdk(sdkInt) == DownloadSchedulerKind.ForegroundServiceFallback

    fun durableQueueItemExtras(queueItemId: String): PersistableBundle =
        PersistableBundle().apply {
            putString(DownloadUserInitiatedJobService.ExtraQueueItemId, queueItemId)
        }

    fun stableJobId(queueItemId: String): Int =
        DownloadUserInitiatedJobService.JobIdBase + ((queueItemId.hashCode().toLong().absoluteValue % DownloadUserInitiatedJobService.JobIdRange).toInt())

    /**
     * JobScheduler IDs are process-global for this app. Hashing into a fixed
     * range is not injective: two distinct durable queue IDs must never replace
     * each other's pending user-initiated transfer.
     */
    fun availableJobId(queueItemId: String, occupied: Map<Int, String?>): Int? {
        val base = DownloadUserInitiatedJobService.JobIdBase
        val range = DownloadUserInitiatedJobService.JobIdRange
        val initialOffset = stableJobId(queueItemId) - base
        // Prefer an already scheduled transfer even if an earlier collision slot
        // has become vacant. Otherwise the same durable item gets two jobs.
        val existing = occupied.entries.firstOrNull { (id, itemId) ->
            id in base until base + range && itemId == queueItemId
        }
        if (existing != null) return existing.key
        for (offset in 0 until range) {
            val candidate = base + (initialOffset + offset) % range
            if (!occupied.containsKey(candidate)) return candidate
        }
        return null
    }
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
        val scheduler = jobScheduler ?: return DownloadScheduleResult(
            kind = DownloadSchedulerKind.UserInitiatedDataTransferJob,
            accepted = false,
        )
        // Serialize lookup + schedule for all scheduler instances in this
        // process. A collision must not silently replace a different job.
        return synchronized(DownloadExecutionSchedulerPolicy) {
            val component = ComponentName(context, DownloadUserInitiatedJobService::class.java)
            val occupied = scheduler.allPendingJobs.associate { pending ->
                pending.id to if (pending.service == component) {
                    pending.extras.getString(DownloadUserInitiatedJobService.ExtraQueueItemId)
                } else {
                    null
                }
            }
            val jobId = DownloadExecutionSchedulerPolicy.availableJobId(request.queueItemId, occupied)
                ?: return@synchronized DownloadScheduleResult(
                    kind = DownloadSchedulerKind.UserInitiatedDataTransferJob,
                    accepted = false,
                )
            val builder = DownloadExecutionSchedulerPolicy.applyNetworkConstraint(
                JobInfo.Builder(jobId, component)
                    .setExtras(DownloadExecutionSchedulerPolicy.durableQueueItemExtras(request.queueItemId)),
                request.networkPreference,
            )

            if (
                DownloadExecutionSchedulerPolicy.supportsEstimatedNetworkBytes(sdkInt) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ) {
                builder.setEstimatedNetworkBytes(
                    DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(request.estimatedDownloadBytes),
                    JobInfo.NETWORK_BYTES_UNKNOWN.toLong(),
                )
            }
            if (
                DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(sdkInt) &&
                Build.VERSION.SDK_INT >= DownloadExecutionSchedulerPolicy.UserInitiatedDataTransferMinSdk
            ) {
                builder.setUserInitiated(true)
            }

            val result = scheduler.schedule(builder.build())
            DownloadScheduleResult(
                kind = DownloadSchedulerKind.UserInitiatedDataTransferJob,
                accepted = result == JobScheduler.RESULT_SUCCESS,
                jobId = jobId,
            )
        }
    }

    private fun startForegroundFallback(request: DownloadScheduleRequest): DownloadScheduleResult {
        ContextCompat.startForegroundService(
            context,
            Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_SCHEDULE_WORK
                putExtra(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, request.queueItemId)
                putExtra(
                    DownloadForegroundService.EXTRA_NETWORK_PREFERENCE,
                    request.networkPreference.name,
                )
            },
        )
        return DownloadScheduleResult(
            kind = DownloadSchedulerKind.ForegroundServiceFallback,
            accepted = true,
            jobId = null,
        )
    }
}

/** Settle JobScheduler ownership even when unexpected worker failures escape. */
internal fun runAndSettleDownloadJob(work: () -> Unit, settle: () -> Unit) {
    try {
        work()
    } finally {
        settle()
    }
}

/**
 * JobScheduler ownership is per attempt, not per durable queue item. The
 * framework can revoke an attempt before its worker returns; a late worker
 * must never call jobFinished on already-stopped parameters.
 */
internal class UidtJobAttempt(private val stopNative: () -> Unit = {}) {
    private enum class State { Running, Stopped, Finished }
    private var state = State.Running
    private val workerExited = CountDownLatch(1)

    @Synchronized
    private fun isStopped(): Boolean = state == State.Stopped

    /** Wait off the JobService callback thread until the prior native writer exits. */
    fun awaitPredecessor(previous: UidtJobAttempt?): Boolean {
        while (previous != null && !previous.workerExited.await(100, TimeUnit.MILLISECONDS)) {
            if (isStopped()) return false
        }
        return !isStopped()
    }

    fun markWorkerExited() = workerExited.countDown()

    fun stop(): Boolean {
        synchronized(this) {
            if (state != State.Running) return false
            state = State.Stopped
        }
        // The platform callback must not hold attempt ownership while
        // signaling a synchronous native worker. A racing completion observes
        // Stopped immediately, even if the native stop call is delayed.
        stopNative()
        return true
    }

    fun finishIfRunning(settle: () -> Unit): Boolean {
        synchronized(this) {
            if (state != State.Running) return false
            state = State.Finished
        }
        settle()
        return true
    }
}

/**
 * Android's JobService callbacks can carry separately unparceled JobParameters
 * objects for the same scheduled job. Reference identity alone therefore cannot
 * locate a running native worker on onStopJob. Match the durable queue identity
 * and framework job ID, but keep registrations ordered across reschedules so
 * an old stop is not redirected to a newer writer waiting for its predecessor.
 */
internal class UidtJobAttemptRegistry<Params : Any> {
    private class Registration<Params : Any>(
        val params: Params,
        val queueItemId: String?,
        val attempt: UidtJobAttempt,
    )

    private val byJobId = mutableMapOf<Int, MutableList<Registration<Params>>>()
    private val byParameters = IdentityHashMap<Params, Registration<Params>>()

    @Synchronized
    fun register(
        jobId: Int,
        params: Params,
        attempt: UidtJobAttempt,
        queueItemId: String? = null,
    ): UidtJobAttempt? {
        val registrations = byJobId.getOrPut(jobId) { mutableListOf() }
        val predecessor = registrations.lastOrNull()?.attempt
        val registration = Registration(params, queueItemId, attempt)
        registrations.add(registration)
        byParameters[params] = registration
        return predecessor
    }

    fun stop(params: Params, jobId: Int? = null, queueItemId: String? = null): Boolean {
        val attempt = synchronized(this) {
            byParameters[params]?.attempt ?: if (!queueItemId.isNullOrBlank()) {
                // A distinct framework JobParameters object must still stop
                // its original worker. The oldest retained matching attempt
                // wins over a replacement for the same durable queue item.
                jobId?.let { byJobId[it] }
                    ?.firstOrNull { it.queueItemId == queueItemId }
                    ?.attempt
            } else null
        } ?: return false
        // Do not hold the registry lock across a native stop signal.
        return attempt.stop()
    }

    @Synchronized
    fun unregister(jobId: Int, params: Params, attempt: UidtJobAttempt) {
        val registration = byParameters[params]
        if (registration?.attempt !== attempt) return
        byParameters.remove(params)
        val registrations = byJobId[jobId] ?: return
        registrations.remove(registration)
        if (registrations.isEmpty()) byJobId.remove(jobId)
    }
}

internal object UidtJobStopPolicy {
    fun shouldReschedule(stopReason: Int): Boolean =
        stopReason != JobParameters.STOP_REASON_USER &&
            stopReason != JobParameters.STOP_REASON_CANCELLED_BY_APP
}

class DownloadUserInitiatedJobService : JobService() {
    // Retain a stopped attempt until its native worker exits. This prevents a
    // new invocation for the same JobScheduler ID from running concurrently.
    private val activeAttempts = UidtJobAttemptRegistry<JobParameters>()

    override fun onStartJob(params: JobParameters): Boolean {
        val queueItemId = params.extras.getString(ExtraQueueItemId)
        if (queueItemId.isNullOrBlank()) {
            // Returning false already tells JobScheduler the job is finished.
            // Calling jobFinished as well would settle the same job twice.
            return false
        }
        val execution = DownloadWorkerExecution()
        val attempt = UidtJobAttempt { execution.requestStop() }
        // JobScheduler may reschedule while a stopped UniFFI call is unwinding.
        // Chain attempts rather than discarding the replacement or overlapping
        // writers against the same durable queue and partial asset.
        val predecessor = activeAttempts.register(params.jobId, params, attempt, queueItemId)
        try {
            ensureNotificationChannel()
            if (Build.VERSION.SDK_INT >= DownloadExecutionSchedulerPolicy.UserInitiatedDataTransferMinSdk) {
                setNotification(
                    params,
                    DownloadServicePolicy.NotificationId,
                    downloadExecutionNotification(),
                    JOB_END_NOTIFICATION_POLICY_REMOVE,
                )
            }
            Thread {
                try {
                    runAndSettleDownloadJob(
                        work = {
                            if (attempt.awaitPredecessor(predecessor)) {
                                execution.execute(this, queueItemId)
                            }
                        },
                        settle = { attempt.finishIfRunning { jobFinished(params, false) } },
                    )
                } finally {
                    attempt.markWorkerExited()
                    activeAttempts.unregister(params.jobId, params, attempt)
                }
            }.start()
            return true
        } catch (failure: Exception) {
            attempt.stop()
            attempt.markWorkerExited()
            activeAttempts.unregister(params.jobId, params, attempt)
            throw failure
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        activeAttempts.stop(params, params.jobId, params.extras.getString(ExtraQueueItemId))
        // User-stopped UIDT jobs must not be automatically rescheduled.
        // Native cooperative stop and retryable durable settlement are still
        // required before this platform-stop path is fully qualified.
        return UidtJobStopPolicy.shouldReschedule(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) params.stopReason
            else JobParameters.STOP_REASON_UNDEFINED,
        )
    }

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
