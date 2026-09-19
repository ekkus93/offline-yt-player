package com.ekkus.offlineytplayer.downloads

import android.content.Context

internal enum class DownloadNotificationPermissionBehavior {
    FullNotifications,
    QueueStateOnly,
}

internal object DownloadNotificationPermissionPolicy {
    const val RuntimePermissionMinSdk = 33
    const val DenialDoesNotMutateDurableQueue = true
    const val DenialRequiresInAppQueueState = true

    fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= RuntimePermissionMinSdk

    fun behavior(granted: Boolean): DownloadNotificationPermissionBehavior =
        if (granted) {
            DownloadNotificationPermissionBehavior.FullNotifications
        } else {
            DownloadNotificationPermissionBehavior.QueueStateOnly
        }
}

object DownloadNotificationPermissionStateStore {
    private const val PreferencesName = "download_notification_permission"
    private const val PermissionGrantedKey = "permission_granted"

    fun recordGrantState(context: Context, granted: Boolean) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PermissionGrantedKey, granted)
            .apply()
    }

    fun recordedGrantState(context: Context): Boolean? {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        return if (preferences.contains(PermissionGrantedKey)) {
            preferences.getBoolean(PermissionGrantedKey, false)
        } else {
            null
        }
    }

    fun clearRecordedGrantState(context: Context) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .remove(PermissionGrantedKey)
            .commit()
    }
}
