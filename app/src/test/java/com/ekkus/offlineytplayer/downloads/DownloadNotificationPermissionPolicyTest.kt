package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationPermissionPolicyTest {
    @Test
    fun runtimePermissionIsOnlyRequiredOnAndroid13AndNewer() {
        assertFalse(DownloadNotificationPermissionPolicy.requiresRuntimePermission(32))
        assertTrue(DownloadNotificationPermissionPolicy.requiresRuntimePermission(33))
        assertTrue(DownloadNotificationPermissionPolicy.requiresRuntimePermission(36))
    }

    @Test
    fun denialKeepsQueueStateAuthoritativeInApp() {
        assertEquals(
            DownloadNotificationPermissionBehavior.QueueStateOnly,
            DownloadNotificationPermissionPolicy.behavior(granted = false),
        )
        assertTrue(DownloadNotificationPermissionPolicy.DenialDoesNotMutateDurableQueue)
        assertTrue(DownloadNotificationPermissionPolicy.DenialRequiresInAppQueueState)
    }

    @Test
    fun mainActivityRequestsPostNotificationsPermission() {
        val activity = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()
        assertTrue(activity.contains("ActivityResultContracts.RequestPermission"))
        assertTrue(activity.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(activity.contains("DownloadNotificationPermissionStateStore.recordGrantState"))
    }
}
