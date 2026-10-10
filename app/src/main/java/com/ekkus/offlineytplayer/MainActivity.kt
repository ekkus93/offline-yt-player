package com.ekkus.offlineytplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
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
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.AppLibraryDetailsGateway
import com.ekkus.offlineytplayer.coregateway.AppLibraryMutationGateway
import com.ekkus.offlineytplayer.coregateway.DownloadPresentationGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiLibraryPlaybackGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiLibraryDetailsGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiLibraryMutationGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.SourceMetadataPolicy
import com.ekkus.offlineytplayer.downloads.AndroidDownloadExecutionScheduler
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionPolicy
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionStateStore
import com.ekkus.offlineytplayer.downloads.SchedulingDownloadControlGateway
import com.ekkus.offlineytplayer.playback.LocalSubtitleTrack
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
    private companion object {
        const val SAVED_LIBRARY_QUERY = "library_query"
        const val MAX_LIBRARY_QUERY_CHARS = 256
    }
    private val uiState: AppUiStateViewModel by viewModels()
    private val bootstrapExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "offline-yt-production-bootstrap").apply { isDaemon = true }
    }
    private var coreGateway: GeneratedUniffiCoreGateway? = null
    private var downloadControlGateway by mutableStateOf<AppDownloadControlGateway?>(null)
    private var libraryPlaybackGateway: GeneratedUniffiLibraryPlaybackGateway? = null
    private var libraryDetailsGateway by mutableStateOf<AppLibraryDetailsGateway?>(null)
    private var libraryMutationGateway by mutableStateOf<AppLibraryMutationGateway?>(null)
    private var sourceAnalysisGateway by mutableStateOf<AppSourceAnalysisGateway?>(null)
    private var downloadPresentationGateway: DownloadPresentationGateway? = null
    private var settingsStore: SharedPreferencesAppSettingsStore? = null
    private var settingsSubscription: SettingsSubscription? = null
    private var stateRefresher: AppStateRefresher? = null
    private var activityStarted = false
    private val statePublicationGate = LifecyclePublicationGate()
    private var settingsSnapshot by mutableStateOf(AppSettingsSnapshot())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> DownloadNotificationPermissionStateStore.recordGrantState(this, granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compose restores the visible search draft; restore the matching repository query too.
        // Otherwise the UI can display a filter that the reopened repository does not apply.
        savedInstanceState?.let { restored ->
            uiState.libraryQuery = restored.getString(SAVED_LIBRARY_QUERY)
                ?.trim()?.take(MAX_LIBRARY_QUERY_CHARS)?.takeIf { it.isNotEmpty() }
        }
        val settings = SharedPreferencesAppSettingsStore.open(this)
        settingsStore = settings
        settingsSnapshot = settings.snapshot()
        settingsSubscription = settings.observe { snapshot ->
            // update() notifies observers on its caller's thread; Compose state belongs on main.
            runOnUiThread {
                if (!isDestroyed) settingsSnapshot = snapshot
            }
        }
        requestNotificationPermissionIfNeeded()
        val sharedUrl = ShareInput.parse(intent?.action, intent?.type, intent?.getStringExtra(Intent.EXTRA_TEXT))
        setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = sharedUrl,
                libraryState = uiState.libraryStateFlow.collectAsState().value,
                downloadsState = uiState.downloadsStateFlow.collectAsState().value,
                downloadControlGateway = downloadControlGateway,
                sourceAnalysisGateway = sourceAnalysisGateway,
                libraryDetailsGatewayProvider = { libraryDetailsGateway },
                libraryMutationGatewayProvider = { libraryMutationGateway },
                libraryRootPath = filesDir.absolutePath,
                onLibraryQueryChanged = { query -> uiState.libraryQuery = query.trim().take(MAX_LIBRARY_QUERY_CHARS).takeIf { it.isNotEmpty() } },
                settingsSnapshot = settingsSnapshot,
                onUpdateSettings = { mutation -> settingsStore?.update(mutation) },
            )
        }
        bootstrapProductionUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SAVED_LIBRARY_QUERY, uiState.libraryQuery)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        statePublicationGate.start()
        stateRefresher?.start()
    }

    override fun onStop() {
        activityStarted = false
        statePublicationGate.stop()
        stateRefresher?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        stateRefresher?.close()
        coreGateway?.close()
        downloadControlGateway?.close()
        libraryPlaybackGateway?.close()
        libraryDetailsGateway?.close()
        libraryMutationGateway?.close()
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
            val controls = runCatching {
                MainActivityDependencyOverrides.createDownloadControl(databasePath)
                    ?: GeneratedUniffiDownloadControlGateway.open(databasePath)
            }
            val playback = runCatching { GeneratedUniffiLibraryPlaybackGateway.open(databasePath) }
            val details = runCatching { GeneratedUniffiLibraryDetailsGateway.open(databasePath) }
            val mutations = runCatching { GeneratedUniffiLibraryMutationGateway.open(databasePath) }
            val sources = runCatching {
                MainActivityDependencyOverrides.createSourceAnalysis()
                    ?: GeneratedUniffiSourceAnalysisGateway.open()
            }
            val presentations = runCatching { DownloadPresentationGateway.open(databasePath) }
            val openedCore = core.getOrNull()
            val startupReconciliation = openedCore?.reconcileStartup()
            val startupFailure = startupReconciliation?.error
            val initialPlaybackAssets = if (startupFailure == null) {
                runCatching { playback.getOrNull()?.listPlaybackAssets() }.getOrNull()
            } else null
            val initialTitles = if (startupFailure == null) {
                runCatching { presentations.getOrThrow().titlesByJobId() }
            } else {
                Result.success(emptyMap())
            }
            val initialLibrary = when {
                core.isFailure -> LibraryScreenState.Failed(SourceMetadataPolicy.diagnostic(core.exceptionOrNull().safeUiMessage()))
                startupFailure != null -> LibraryScreenState.Failed(startupFailure.startupReconciliationDiagnostic())
                else -> openedCore!!.listLibrary(uiState.libraryQuery).toLibraryScreenState(libraryRoot, initialPlaybackAssets)
            }
            val initialDownloads = when {
                core.isFailure -> DownloadsScreenState.Failed(SourceMetadataPolicy.diagnostic(core.exceptionOrNull().safeUiMessage()))
                startupFailure != null -> DownloadsScreenState.Failed(startupFailure.startupReconciliationDiagnostic())
                initialTitles.isFailure -> DownloadsScreenState.Failed("Download presentation metadata is unavailable.")
                else -> openedCore!!.listDownloadQueue().toDownloadsScreenState(initialTitles.getOrThrow())
            }
            if (isFinishing || isDestroyed) {
                core.getOrNull()?.close(); controls.getOrNull()?.close(); playback.getOrNull()?.close(); details.getOrNull()?.close(); mutations.getOrNull()?.close(); sources.getOrNull()?.close()
                return@execute
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    core.getOrNull()?.close(); controls.getOrNull()?.close(); playback.getOrNull()?.close(); details.getOrNull()?.close(); mutations.getOrNull()?.close(); sources.getOrNull()?.close()
                    return@runOnUiThread
                }
                uiState.libraryState = initialLibrary
                uiState.downloadsState = initialDownloads
                coreGateway = core.getOrNull()
                downloadControlGateway = controls.getOrNull()?.let { controlsGateway ->
                    SchedulingDownloadControlGateway(
                        delegate = controlsGateway,
                        scheduler = AndroidDownloadExecutionScheduler(applicationContext),
                        settingsSnapshot = { settingsSnapshot },
                    )
                }
                libraryPlaybackGateway = playback.getOrNull()
                libraryDetailsGateway = details.getOrNull()
                libraryMutationGateway = mutations.getOrNull()
                sourceAnalysisGateway = sources.getOrNull()
                downloadPresentationGateway = presentations.getOrNull()
                coreGateway?.takeIf { startupFailure == null }?.let { gateway ->
                    stateRefresher = AppStateRefresher(
                        gateway = gateway,
                        onLibrary = { result ->
                            // Capture before blocking FFI so a stop/restart cannot publish an old result.
                            val token = statePublicationGate.capture()
                            if (token != null) {
                                val playbackResult = runCatching { libraryPlaybackGateway?.listPlaybackAssets() }.getOrNull()
                                runOnUiThread {
                                    if (!isDestroyed && statePublicationGate.permits(token)) {
                                        uiState.libraryState = result.toLibraryScreenState(libraryRoot, playbackResult)
                                    }
                                }
                            }
                        },
                        onDownloads = { result ->
                            val token = statePublicationGate.capture()
                            if (token != null) {
                                val titles = runCatching {
                                    checkNotNull(downloadPresentationGateway) { "Download presentation gateway is unavailable" }
                                        .titlesByJobId()
                                }
                                runOnUiThread {
                                    if (!isDestroyed && statePublicationGate.permits(token)) {
                                        uiState.downloadsState = titles.fold(
                                            onSuccess = { result.toDownloadsScreenState(it) },
                                            onFailure = { DownloadsScreenState.Failed("Download presentation metadata is unavailable.") },
                                        )
                                    }
                                }
                            }
                        },
                        libraryQuery = { uiState.libraryQuery },
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

internal fun CoreGatewayResult<List<CoreLibraryItem>>.toLibraryScreenState(
    libraryRoot: File,
    playbackAssets: CoreGatewayResult<List<CoreLibraryPlaybackAsset>>? = null,
): LibraryScreenState {
    error?.let { return LibraryScreenState.Failed(SourceMetadataPolicy.diagnostic(it.message)) }
    // A missing, failed, or malformed playback-gateway result is not an empty library.
    // Treat it as an unavailable playback index instead of silently disabling Play for every item.
    val libraryItems = value ?: return LibraryScreenState.Failed("Library repository data is unavailable.")
    val availablePlaybackAssets = playbackAssets?.takeIf { it.error == null }?.value
        ?: return LibraryScreenState.Failed("Library playback metadata is unavailable.")
    // Duplicate or blank durable identities would make associateBy silently overwrite records.
    // Fail closed rather than attach the wrong playback asset to a Library row.
    if (libraryItems.any { it.itemId.isBlank() } ||
        libraryItems.map { it.itemId }.toSet().size != libraryItems.size ||
        availablePlaybackAssets.any { it.itemId.isBlank() } ||
        availablePlaybackAssets.map { it.itemId }.toSet().size != availablePlaybackAssets.size
    ) {
        return LibraryScreenState.Failed("Library identity metadata is invalid.")
    }
    val playbackByItemId = availablePlaybackAssets.associateBy { it.itemId }
    return LibraryScreenState.Ready(libraryItems.map { item ->
        val playback = playbackByItemId[item.itemId]
        LibraryRowModel(
            id = item.itemId,
            title = SourceMetadataPolicy.title(item.displayTitle),
            detail = item.libraryDetail(playback),
            completed = item.completed && playback?.playable == true,
            videoPath = playback?.takeIf { it.playable }?.videoRelativePath?.let { libraryRoot.resolve(it).absolutePath },
            audioPath = playback?.takeIf { it.playable }?.audioRelativePath?.let { libraryRoot.resolve(it).absolutePath },
            subtitleTracks = playback?.takeIf { it.playable }?.subtitleTracks.orEmpty().map { track ->
                LocalSubtitleTrack(
                    path = libraryRoot.resolve(track.relativePath).absolutePath,
                    language = track.language,
                    label = track.language,
                    mimeType = track.mimeType,
                )
            },
            resumePositionMs = item.playbackPositionMs,
        )
    })
}

private fun CoreLibraryItem.libraryDetail(playback: CoreLibraryPlaybackAsset?): String = listOfNotNull(
    qualityLabel.let(SourceMetadataPolicy::qualityLabel),
    durationMs?.let(::formatDuration),
    playback?.takeUnless { it.playable }?.unavailableReason?.let(SourceMetadataPolicy::diagnostic),
).joinToString(" · ")

internal fun CoreGatewayResult<List<CoreDownloadSnapshot>>.toDownloadsScreenState(
    titlesByJobId: Map<String, String>,
): DownloadsScreenState {
    error?.let { return DownloadsScreenState.Failed(SourceMetadataPolicy.diagnostic(it.message)) }
    val snapshots = value ?: return DownloadsScreenState.Failed("Download queue data is unavailable.")
    if (snapshots.any { it.jobId.isBlank() } ||
        snapshots.map { it.jobId }.toSet().size != snapshots.size
    ) {
        return DownloadsScreenState.Failed("Download queue identity metadata is invalid.")
    }
    if (snapshots.any { titlesByJobId[it.jobId].isNullOrBlank() }) {
        return DownloadsScreenState.Failed("Download presentation metadata is unavailable.")
    }
    return DownloadsScreenState.Ready(snapshots.map { snapshot ->
        val total = snapshot.totalBytes
        val percent = if (total != null && total > 0) ((snapshot.bytesDownloaded.coerceAtMost(total) * 100L) / total).toInt() else 0
        DownloadRowModel(
            id = snapshot.jobId,
            title = SourceMetadataPolicy.title(checkNotNull(titlesByJobId[snapshot.jobId])),
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
    CoreDownloadState.CANCELED -> DownloadUiState.Canceled
    else -> DownloadUiState.Active
}

private fun CoreGatewayError.startupReconciliationDiagnostic(): String =
    SourceMetadataPolicy.diagnostic("Startup reconciliation failed: $message")

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Throwable?.safeUiMessage(): String = this?.message?.take(256) ?: this?.javaClass?.simpleName ?: "Unknown core startup failure"
