package com.ekkus.offlineytplayer.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ekkus.offlineytplayer.MainActivity
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.settings.SharedPreferencesAppSettingsStore
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
    const val DefaultConcurrentDownloads = 2
    const val SupportsPauseResumeCancel = true
    const val ReportsCompletionAndFailure = true
    const val ReconcilesDurableQueueOnStart = true
    const val HonorsNetworkPreference = true
    const val SupportsBootRecovery = true
    const val FailsInterruptedTransfersExplicitly = true

    /**
     * The maximum is owned by the portable core and exported through UniFFI. Android deliberately
     * has no duplicate maximum constant; generated binding verification in CI is the contract that
     * makes [ffiMaxConcurrentDownloads] available to the platform integration layer.
     */
    fun concurrentDownloads(requested: Int, coreMaximum: Int): Int =
        requested.coerceIn(1, coreMaximum)
}

internal object DownloadForegroundServiceInventory {
    const val RetainedDataSyncService = "com.ekkus.offlineytplayer.downloads.DownloadForegroundService"
    const val RetainedMediaProcessingServices = 0
    const val HandlesAndroid15Timeout = true
}

internal object DownloadForegroundTimeoutStore {
    private const val PreferencesName = "download_foreground_timeout"
    private const val LastStartIdKey = "last_start_id"
    private const val LastForegroundServiceTypeKey = "last_foreground_service_type"
    private const val TimeoutCountKey = "timeout_count"

    fun persistTimeout(
        context: Context,
        startId: Int,
        foregroundServiceType: Int,
    ) {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        preferences.edit()
            .putInt(LastStartIdKey, startId)
            .putInt(LastForegroundServiceTypeKey, foregroundServiceType)
            .putInt(TimeoutCountKey, preferences.getInt(TimeoutCountKey, 0) + 1)
            .apply()
    }
}

class DownloadForegroundService : Service() {
    private val workerRunning = AtomicBoolean(false)
    @Volatile private var activeQueueItemId: String? = null
    @Volatile private var activeNetworkPreference = DownloadNetworkPreference.AnyNetwork
    @Volatile private var lastConnectivity = DownloadConnectivity.None
    @Volatile private var lastNetworkDecision = DownloadNetworkDecision.PauseForConnectivity
    private lateinit var connectivityPauseRegistry: SharedPreferencesDownloadConnectivityPauseRegistry
    private var connectivityObserver: Closeable? = null
    private var settingsStore: SharedPreferencesAppSettingsStore? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        connectivityPauseRegistry = SharedPreferencesDownloadConnectivityPauseRegistry(this)
        settingsStore = SharedPreferencesAppSettingsStore.open(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val queueItemId = intent?.getStringExtra(EXTRA_QUEUE_ITEM_ID)
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                queueItemId?.let(connectivityPauseRegistry::clearPausedByConnectivity)
                dispatchControlAction(intent)
            }
            ACTION_RESUME -> {
                queueItemId?.let(connectivityPauseRegistry::clearPausedByConnectivity)
                dispatchControlAction(intent)
                configureActiveWork(intent, queueItemId)
            }
            ACTION_CANCEL -> {
                queueItemId?.let(connectivityPauseRegistry::clearPausedByConnectivity)
                dispatchControlAction(intent)
            }
            ACTION_CONNECTIVITY_RETRY,
            ACTION_SCHEDULE_WORK,
            -> configureActiveWork(intent, queueItemId)
            ACTION_RECONCILE_AFTER_REBOOT -> Unit
        }
        startForeground(DownloadServicePolicy.NotificationId, activeNotification(queueItemId))
        if (
            intent?.action == ACTION_SCHEDULE_WORK ||
            intent?.action == ACTION_RESUME ||
            intent?.action == ACTION_CONNECTIVITY_RETRY
        ) {
            ensureConnectivityObserver()
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        DownloadForegroundTimeoutStore.persistTimeout(
            context = this,
            startId = startId,
            foregroundServiceType = fgsType,
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectivityObserver?.close()
        connectivityObserver = null
        settingsStore?.close()
        settingsStore = null
        super.onDestroy()
    }

    private fun configureActiveWork(intent: Intent?, queueItemId: String?) {
        if (queueItemId.isNullOrBlank()) return
        activeQueueItemId = queueItemId
        activeNetworkPreference = intent?.getStringExtra(EXTRA_NETWORK_PREFERENCE)
            ?.let { encoded ->
                DownloadNetworkPreference.values().firstOrNull { it.name == encoded }
            }
            ?: settingsStore?.snapshot()?.let { settings ->
                if (settings.wifiOnlyDownloads) {
                    DownloadNetworkPreference.WifiOnly
                } else {
                    DownloadNetworkPreference.AnyNetwork
                }
            }
            ?: DownloadNetworkPreference.AnyNetwork
    }

    private fun ensureConnectivityObserver() {
        if (connectivityObserver != null) {
            // A registered callback does not replay its current network state for a new
            // schedule/resume intent. Kick durable work using the last observed state.
            Thread { handleConnectivityChange(lastConnectivity) }.start()
            return
        }
        val handleChange: (DownloadConnectivity) -> Unit = { connectivity ->
            lastConnectivity = connectivity
            lastNetworkDecision = DownloadNetworkPolicy.decision(activeNetworkPreference, connectivity)
            Thread {
                handleConnectivityChange(connectivity)
            }.start()
        }
        connectivityObserver = connectivityObserverFactoryForTesting?.invoke(this, handleChange)
            ?: AndroidDownloadConnectivityObserver(
                context = this,
                onConnectivityChanged = handleChange,
            ).also { it.start() }
    }

    private fun handleConnectivityChange(connectivity: DownloadConnectivity) {
        val queueItemId = activeQueueItemId ?: return
        val decision = DownloadNetworkPolicy.decision(activeNetworkPreference, connectivity)
        lastNetworkDecision = decision

        // Never resume a connectivity pause before the active transfer thread has observed the
        // durable PAUSED state and returned. Resuming too early could race the worker's pause check.
        if (decision == DownloadNetworkDecision.Allow && workerRunning.get()) return

        runConnectivityCoordinator(connectivity)
        if (decision == DownloadNetworkDecision.Allow) {
            launchWorkerIfEligible(queueItemId)
        }
    }

    private fun runConnectivityCoordinator(
        connectivity: DownloadConnectivity,
    ): DownloadConnectivityCoordinatorReport {
        val databasePath = downloadDatabasePath()
        return GeneratedUniffiCoreGateway.open(databasePath).use { core ->
            GeneratedUniffiDownloadControlGateway.open(databasePath).use { controls ->
                DownloadConnectivityCoordinator(
                    coreGateway = core,
                    controlGateway = controls,
                    networkPreference = { activeNetworkPreference },
                    connectivityPauseRegistry = connectivityPauseRegistry,
                ).onConnectivityChanged(connectivity)
            }
        }
    }

    private fun launchWorkerIfEligible(queueItemId: String) {
        if (lastNetworkDecision != DownloadNetworkDecision.Allow) return
        if (!workerRunning.compareAndSet(false, true)) return

        Thread {
            try {
                DownloadWorkerExecutor.execute(this, queueItemId)
            } finally {

                workerRunning.set(false)
                settleWorkerAfterExecution(queueItemId)
            }
        }.start()
    }

    private fun settleWorkerAfterExecution(queueItemId: String) {
        if (activeQueueItemId != queueItemId) return

        if (
            lastNetworkDecision == DownloadNetworkDecision.PauseForConnectivity &&
            currentDownloadState(queueItemId)?.let { state ->
                state == CoreDownloadState.QUEUED ||
                    state == CoreDownloadState.RESOLVING ||
                    state == CoreDownloadState.DOWNLOADING ||
                    state == CoreDownloadState.RETRY_WAIT ||
                    state == CoreDownloadState.VERIFYING
            } == true
        ) {
            runConnectivityCoordinator(lastConnectivity)
        }

        var state = currentDownloadState(queueItemId)
        if (
            state == CoreDownloadState.PAUSED &&
            connectivityPauseRegistry.wasPausedByConnectivity(queueItemId) &&
            lastNetworkDecision == DownloadNetworkDecision.Allow
        ) {
            runConnectivityCoordinator(lastConnectivity)
            state = currentDownloadState(queueItemId)
        }

        if (state == CoreDownloadState.QUEUED && lastNetworkDecision == DownloadNetworkDecision.Allow) {
            launchWorkerIfEligible(queueItemId)
            return
        }
        if (
            state == CoreDownloadState.PAUSED &&
            connectivityPauseRegistry.wasPausedByConnectivity(queueItemId)
        ) {
            return
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun currentDownloadState(queueItemId: String): CoreDownloadState? =
        GeneratedUniffiCoreGateway.open(downloadDatabasePath()).use { core ->
            core.listDownloadQueue().value
                ?.firstOrNull { it.jobId == queueItemId }
                ?.state
        }

    private fun dispatchControlAction(intent: Intent): Boolean = try {
        GeneratedUniffiDownloadControlGateway.open(downloadDatabasePath()).use { gateway ->
            DownloadForegroundControlDispatcher.dispatch(
                action = intent.action,
                queueItemId = intent.getStringExtra(EXTRA_QUEUE_ITEM_ID),
                gateway = gateway,
            )
        }
    } catch (_: RuntimeException) {
        false
    }

    private fun downloadDatabasePath(): String = File(filesDir, PRODUCTION_DATABASE_NAME).absolutePath

    private fun activeNotification(queueItemId: String?): Notification {
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
            .addAction(android.R.drawable.ic_media_pause, "Pause", serviceAction(ACTION_PAUSE, 1, queueItemId))
            .addAction(android.R.drawable.ic_media_play, "Resume", serviceAction(ACTION_RESUME, 2, queueItemId))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", serviceAction(ACTION_CANCEL, 3, queueItemId))
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int, queueItemId: String?): PendingIntent {
        val intent = Intent(this, DownloadForegroundService::class.java).setAction(action)
        if (!queueItemId.isNullOrBlank()) {
            intent.putExtra(EXTRA_QUEUE_ITEM_ID, queueItemId)
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

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
        // Instrumentation-only seam: a test can deliver the initial network state once,
        // then deliberately withhold subsequent callbacks to prove schedule-intent reuse.
        // Production always uses the real AndroidDownloadConnectivityObserver.
        @Volatile
        internal var connectivityObserverFactoryForTesting:
            ((Context, (DownloadConnectivity) -> Unit) -> Closeable)? = null

        private const val PRODUCTION_DATABASE_NAME = "offline-yt-player.sqlite3"
        const val ACTION_PAUSE = "com.ekkus.offlineytplayer.download.PAUSE"
        const val ACTION_RESUME = "com.ekkus.offlineytplayer.download.RESUME"
        const val ACTION_CANCEL = "com.ekkus.offlineytplayer.download.CANCEL"
        const val ACTION_STOP = "com.ekkus.offlineytplayer.download.STOP"
        const val ACTION_CONNECTIVITY_RETRY = "com.ekkus.offlineytplayer.download.CONNECTIVITY_RETRY"
        const val ACTION_RECONCILE_AFTER_REBOOT = "com.ekkus.offlineytplayer.download.RECONCILE_AFTER_REBOOT"
        const val ACTION_SCHEDULE_WORK = "com.ekkus.offlineytplayer.download.SCHEDULE_WORK"
        const val EXTRA_QUEUE_ITEM_ID = "queue_item_id"
        const val EXTRA_NETWORK_PREFERENCE = "network_preference"
    }
}
