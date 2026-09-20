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
import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionPolicy
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionStateStore
import com.ekkus.offlineytplayer.ui.LibraryRowModel
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import com.ekkus.offlineytplayer.ui.OfflineYTPlayerApp

class MainActivity : ComponentActivity() {
    private var coreGateway: AppCoreGateway? = null
    private var downloadControlGateway by mutableStateOf<AppDownloadControlGateway?>(null)
    private var libraryState by mutableStateOf<LibraryScreenState>(LibraryScreenState.Loading)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        DownloadNotificationPermissionStateStore.recordGrantState(this, granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val sharedUrl = ShareInput.parse(
            intent?.action,
            intent?.type,
            intent?.getStringExtra(Intent.EXTRA_TEXT),
        )
        setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = sharedUrl,
                libraryState = libraryState,
                downloadControlGateway = downloadControlGateway,
            )
        }
        openProductionGateways()
    }

    override fun onDestroy() {
        downloadControlGateway?.close()
        downloadControlGateway = null
        coreGateway?.close()
        coreGateway = null
        super.onDestroy()
    }

    private fun openProductionGateways() {
        val databasePath = filesDir.resolve("offline_yt_player.db").absolutePath
        Thread({
            try {
                val libraryGateway = GeneratedUniffiCoreGateway.open(databasePath)
                val controlGateway = GeneratedUniffiDownloadControlGateway.open(databasePath)
                val result = libraryGateway.listLibrary()
                val nextState = result.error?.let { error ->
                    LibraryScreenState.Failed(error.message)
                } ?: LibraryScreenState.Ready(
                    result.value.orEmpty().map { item ->
                        LibraryRowModel(
                            id = item.itemId,
                            title = item.displayTitle,
                            detail = buildString {
                                append(item.qualityLabel)
                                item.durationMs?.let { durationMs -> append(" · ${durationMs / 1000}s") }
                            },
                            completed = item.completed,
                            resumePositionMs = item.playbackPositionMs,
                        )
                    },
                )
                runOnUiThread {
                    if (isDestroyed) {
                        controlGateway.close()
                        libraryGateway.close()
                    } else {
                        coreGateway = libraryGateway
                        downloadControlGateway = controlGateway
                        libraryState = nextState
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed) {
                        libraryState = LibraryScreenState.Failed(
                            error.message ?: "Unable to open the local media repository.",
                        )
                    }
                }
            }
        }, "offline-yt-production-composition-root").apply {
            isDaemon = true
            start()
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
