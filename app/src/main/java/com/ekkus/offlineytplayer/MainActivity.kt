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
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionPolicy
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionStateStore
import com.ekkus.offlineytplayer.ui.OfflineYTPlayerApp

class MainActivity : ComponentActivity() {
    private var coreGateway: GeneratedUniffiCoreGateway? = null
    private var downloadControlGateway: GeneratedUniffiDownloadControlGateway? = null

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
        val databaseRoot = filesDir.resolve("offline-yt-player").apply { mkdirs() }
        val databasePath = databaseRoot.resolve("library.sqlite").absolutePath
        val core = GeneratedUniffiCoreGateway.open(databasePath)
        val controls = GeneratedUniffiDownloadControlGateway.open(databasePath)
        coreGateway = core
        downloadControlGateway = controls
        setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = sharedUrl,
                coreGateway = core,
                downloadControlGateway = controls,
            )
        }
    }

    override fun onDestroy() {
        downloadControlGateway?.close()
        downloadControlGateway = null
        coreGateway?.close()
        coreGateway = null
        super.onDestroy()
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
