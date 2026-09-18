package com.ekkus.offlineytplayer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionStateStore
import com.ekkus.offlineytplayer.downloads.DownloadNotificationPermissionPolicy
import com.ekkus.offlineytplayer.ui.OfflineYTPlayerApp

class MainActivity : ComponentActivity() {
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
        setContent { OfflineYTPlayerApp(initialSharedUrl = sharedUrl) }
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
