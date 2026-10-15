package com.ekkus.offlineytplayer.downloads

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
    }
}
