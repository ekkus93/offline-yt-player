package com.ekkus.offlineytplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiSourceAnalysisGateway
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionPolicy
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionStateStore
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
    private var sourceAnalysisGateway: GeneratedUniffiSourceAnalysisGateway? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> DownloadNotificationPermissionStateStore.recordGrantState(this, granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val sharedUrl = ShareInput.parse(intent?.action, intent?.type, intent?.getStringExtra(Intent.EXTRA_TEXT))
        setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = sharedUrl,
                libraryState = LibraryScreenState.Loading,
                downloadsState = DownloadsScreenState.Loading,
            )
        }
        bootstrapProductionUi(sharedUrl)
    }

    override fun onDestroy() {
        coreGateway?.close()
        downloadControlGateway?.close()
        sourceAnalysisGateway?.close()
        bootstrapExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bootstrapProductionUi(sharedUrl: String?) {
        val databasePath = File(filesDir, "offline-yt-player.sqlite3").absolutePath
        bootstrapExecutor.execute {
            val core = runCatching { GeneratedUniffiCoreGateway.open(databasePath) }
            val controls = runCatching { GeneratedUniffiDownloadControlGateway.open(databasePath) }
            val sources = runCatching { GeneratedUniffiSourceAnalysisGateway.open() }
            val libraryState = core.fold(
                onSuccess = { it.listLibrary().toLibraryScreenState() },
                onFailure = { LibraryScreenState.Failed(it.safeUiMessage()) },
            )
            val downloadsState = core.fold(
                onSuccess = { it.listDownloadQueue().toDownloadsScreenState() },
                onFailure = { DownloadsScreenState.Failed(it.safeUiMessage()) },
            )
            if (isFinishing || isDestroyed) {
                core.getOrNull()?.close(); controls.getOrNull()?.close(); sources.getOrNull()?.close()
                return@execute
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    core.getOrNull()?.close(); controls.getOrNull()?.close(); sources.getOrNull()?.close()
                    return@runOnUiThread
                }
                coreGateway = core.getOrNull()
                downloadControlGateway = controls.getOrNull()
                sourceAnalysisGateway = sources.getOrNull()
                setContent {
                    OfflineYTPlayerApp(
                        initialSharedUrl = sharedUrl,
                        libraryState = libraryState,
                        downloadsState = downloadsState,
                        downloadControlGateway = downloadControlGateway,
                        sourceAnalysisGateway = sourceAnalysisGateway,
                    )
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

private fun com.ekkus.offlineytplayer.coregateway.CoreGatewayResult<List<CoreLibraryItem>>.toLibraryScreenState(): LibraryScreenState {
    error?.let { return LibraryScreenState.Failed(it.message) }
    return LibraryScreenState.Ready(value.orEmpty().map { item ->
        LibraryRowModel(item.itemId, item.displayTitle, listOfNotNull(item.qualityLabel, item.durationMs?.let(::formatDuration)).joinToString(" · "), item.completed, item.playbackPositionMs)
    })
}

private fun com.ekkus.offlineytplayer.coregateway.CoreGatewayResult<List<CoreDownloadSnapshot>>.toDownloadsScreenState(): DownloadsScreenState {
    error?.let { return DownloadsScreenState.Failed(it.message) }
    return DownloadsScreenState.Ready(value.orEmpty().map { snapshot ->
        val total = snapshot.totalBytes
        val percent = if (total != null && total > 0) ((snapshot.bytesDownloaded.coerceAtMost(total) * 100L) / total).toInt() else 0
        DownloadRowModel(snapshot.jobId, snapshot.jobId, snapshot.state.toUiState(), percent, if (total == null) "${snapshot.bytesDownloaded} bytes" else "${snapshot.bytesDownloaded} / $total bytes", snapshot.lastError?.message)
    })
}

private fun CoreDownloadState.toUiState(): DownloadUiState = when (this) {
    CoreDownloadState.PAUSED -> DownloadUiState.Paused
    CoreDownloadState.FAILED -> DownloadUiState.Failed
    CoreDownloadState.COMPLETED -> DownloadUiState.Completed
    else -> DownloadUiState.Active
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Throwable.safeUiMessage(): String = message?.take(256) ?: javaClass.simpleName
