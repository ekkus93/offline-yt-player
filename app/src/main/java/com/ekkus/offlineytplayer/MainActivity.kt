package com.ekkus.offlineytplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import com.ekkus.offlineytplayer.coregateway.CoreLibraryPlaybackAsset
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiLibraryPlaybackGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.SourceMetadataPolicy
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionPolicy
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionStateStore
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.settings.SettingsSubscription
import com.ekkus.offlineytplayer.settings.SharedPreferencesAppSettingsStore
import com.ekkus.offlineytplayer.ui.DownloadRowModel
import com.ekkus.offlineytplayer.ui.DownloadUiState
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import com.ekkus.offlineytplayer.ui.LibraryRowModel
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import com.ekkus.offlineytplayer.ui.OfflineYTPlayerApp
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val bootstrapExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "offline-yt-production-bootstrap").apply { isDaemon = true }
    }
    private var coreGateway: GeneratedUniffiCoreGateway? = null
    private var downloadControlGateway: GeneratedUniffiDownloadControlGateway? = null
    private var libraryPlaybackGateway: GeneratedUniffiLibraryPlaybackGateway? = null
    private var sourceAnalysisGateway: GeneratedUniffiSourceAnalysisGateway? = null
    private var settingsStore: SharedPreferencesAppSettingsStore? = null
    private var settingsSubscription: SettingsSubscription? = null
    private var stateRefresher: AppStateRefresher? = null
    private var activityStarted = false
    private var libraryState by mutableStateOf<LibraryScreenState>(LibraryScreenState.Loading)
    private var downloadsState by mutableStateOf<DownloadsScreenState>(DownloadsScreenState.Loading)
    private var settingsSnapshot by mutableStateOf(AppSettingsSnapshot())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> DownloadNotificationPermissionStateStore.recordGrantState(this, granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SharedPreferencesAppSettingsStore.open(this)
        settingsStore = settings
        settingsSnapshot = settings.snapshot()
        settingsSubscription = settings.observe { snapshot -> settingsSnapshot = snapshot }
        requestNotificationPermissionIfNeeded()
        val sharedUrl = ShareInput.parse(intent?.action, intent?.type, intent?.getStringExtra(Intent.EXTRA_TEXT))
        setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = sharedUrl,
                libraryState = libraryState,
                downloadsState = downloadsState,
                downloadControlGateway = downloadControlGateway,
                sourceAnalysisGateway = sourceAnalysisGateway,
                settingsSnapshot = settingsSnapshot,
                onUpdateSettings = { mutation -> settingsStore?.update(mutation) },
            )
        }
        bootstrapProductionUi()
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        stateRefresher?.start()
    }

    override fun onStop() {
        activityStarted = false
        stateRefresher?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        stateRefresher?.close()
        coreGateway?.close()
        downloadControlGateway?.close()
        libraryPlaybackGateway?.close()
        sourceAnalysisGateway?.close()
        settingsSubscription?.close()
        settingsStore?.close()
        bootstrapExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bootstrapProductionUi() {
        val databasePath = File(filesDir, "offline-yt-player.sqlite3").absolutePath
        val libraryRoot = filesDir
        bootstrapExecutor.execute {
            val core = runCatching { GeneratedUniffiCoreGateway.open(databasePath) }
            val controls = runCatching { GeneratedUniffiDownloadControlGateway.open(databasePath) }
            val playback = runCatching { GeneratedUniffiLibraryPlaybackGateway.open(databasePath) }
            val sources = runCatching { GeneratedUniffiSourceAnalysisGateway.open() }
            val openedCore = core.getOrNull()
            val startupReconciliation = openedCore?.reconcileStartup()
            val startupFailure = startupReconciliation?.error
            val initialPlaybackAssets = if (startupFailure == null) playback.getOrNull()?.listPlaybackAssets() else null
            val initialLibrary = when {
                core.isFailure -> LibraryScreenState.Failed(SourceMetadataPolicy.diagnostic(core.exceptionOrNull().safeUiMessage()))
                startupFailure != null -> LibraryScreenState.Failed(startupFailure.startupReconciliationDiagnostic())
                else -> openedCore!!.listLibrary().toLibraryScreenState(libraryRoot, initialPlaybackAssets)
            }
            val initialDownloads = when {
                core.isFailure -> DownloadsScreenState.Failed(SourceMetadataPolicy.diagnostic(core.exceptionOrNull().safeUiMessage()))
                startupFailure != null -> DownloadsScreenState.Failed(startupFailure.startupReconciliationDiagnostic())
                else -> openedCore!!.listDownloadQueue().toDownloadsScreenState()
            }
            if (isFinishing || isDestroyed) {
                core.getOrNull()?.close(); controls.getOrNull()?.close(); playback.getOrNull()?.close(); sources.getOrNull()?.close()
                return@execute
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    core.getOrNull()?.close(); controls.getOrNull()?.close(); playback.getOrNull()?.close(); sources.getOrNull()?.close()
                    return@runOnUiThread
                }
                libraryState = initialLibrary
                downloadsState = initialDownloads
                coreGateway = core.getOrNull()
                downloadControlGateway = controls.getOrNull()
                libraryPlaybackGateway = playback.getOrNull()
                sourceAnalysisGateway = sources.getOrNull()
                coreGateway?.takeIf { startupFailure == null }?.let { gateway ->
                    stateRefresher = AppStateRefresher(
                        gateway = gateway,
                        onLibrary = { result ->
                            val playbackResult = libraryPlaybackGateway?.listPlaybackAssets()
                            runOnUiThread {
                                if (!isDestroyed) libraryState = result.toLibraryScreenState(libraryRoot, playbackResult)
                            }
                        },
                        onDownloads = { result -> runOnUiThread { if (!isDestroyed) downloadsState = result.toDownloadsScreenState() } },
                    ).also { if (activityStarted) it.start() }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (!DownloadNotificationPermissionPolicy.requiresRuntimePermission(Build.VERSION.SDK_INT)) return
        val permissionState = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            DownloadNotificationPermissionStateStore.recordGrantState(this, true)
            return
        }
        DownloadNotificationPermissionStateStore.recordGrantState(this, false)
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun CoreGatewayResult<List<CoreLibraryItem>>.toLibraryScreenState(
    libraryRoot: File,
    playbackAssets: CoreGatewayResult<List<CoreLibraryPlaybackAsset>>? = null,
): LibraryScreenState {
    error?.let { return LibraryScreenState.Failed(SourceMetadataPolicy.diagnostic(it.message)) }
    val playbackByItemId = playbackAssets?.value.orEmpty().associateBy { it.itemId }
    return LibraryScreenState.Ready(value.orEmpty().map { item ->
        val playback = playbackByItemId[item.itemId]
        LibraryRowModel(
            id = item.itemId,
            title = SourceMetadataPolicy.title(item.displayTitle),
            detail = item.libraryDetail(playback),
            completed = item.completed && playback?.playable == true,
            videoPath = playback?.takeIf { it.playable }?.videoRelativePath?.let { libraryRoot.resolve(it).absolutePath },
            audioPath = playback?.takeIf { it.playable }?.audioRelativePath?.let { libraryRoot.resolve(it).absolutePath },
            resumePositionMs = item.playbackPositionMs,
        )
    })
}

private fun CoreLibraryItem.libraryDetail(playback: CoreLibraryPlaybackAsset?): String = listOfNotNull(
    qualityLabel.let(SourceMetadataPolicy::qualityLabel),
    durationMs?.let(::formatDuration),
    playback?.takeUnless { it.playable }?.unavailableReason?.let(SourceMetadataPolicy::diagnostic),
).joinToString(" · ")

private fun CoreGatewayResult<List<CoreDownloadSnapshot>>.toDownloadsScreenState(): DownloadsScreenState {
    error?.let { return DownloadsScreenState.Failed(SourceMetadataPolicy.diagnostic(it.message)) }
    return DownloadsScreenState.Ready(value.orEmpty().map { snapshot ->
        val total = snapshot.totalBytes
        val percent = if (total != null && total > 0) ((snapshot.bytesDownloaded.coerceAtMost(total) * 100L) / total).toInt() else 0
        DownloadRowModel(
            id = snapshot.jobId,
            title = SourceMetadataPolicy.title(snapshot.jobId),
            state = snapshot.state.toUiState(),
            percent = percent,
            size = if (total == null) "${snapshot.bytesDownloaded} bytes" else "${snapshot.bytesDownloaded} / $total bytes",
            error = snapshot.lastError?.message?.let(SourceMetadataPolicy::diagnostic),
        )
    })
}

private fun CoreDownloadState.toUiState(): DownloadUiState = when (this) {
    CoreDownloadState.PAUSED -> DownloadUiState.Paused
    CoreDownloadState.FAILED -> DownloadUiState.Failed
    CoreDownloadState.COMPLETED -> DownloadUiState.Completed
    else -> DownloadUiState.Active
}

private fun CoreGatewayError.startupReconciliationDiagnostic(): String =
    SourceMetadataPolicy.diagnostic("Startup reconciliation failed: $message")

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Throwable?.safeUiMessage(): String = this?.message?.take(256) ?: this?.javaClass?.simpleName ?: "Unknown core startup failure"
