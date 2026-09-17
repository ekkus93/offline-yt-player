package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationPolicyTest {
    @Test
    fun notificationPolicyExposesRequiredActionsAndTerminalReporting() {
        assertTrue(DownloadServicePolicy.SupportsPauseResumeCancel)
        assertTrue(DownloadServicePolicy.ReportsCompletionAndFailure)
    }

    @Test
    fun activeNotificationIsOngoingProgressWithVisibleControls() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        assertTrue(service.contains("setOngoing(true)"))
        assertTrue(service.contains("setProgress(100, 0, true)"))
        assertTrue(service.contains("\"Pause\", serviceAction(ACTION_PAUSE"))
        assertTrue(service.contains("\"Resume\", serviceAction(ACTION_RESUME"))
        assertTrue(service.contains("\"Cancel\", serviceAction(ACTION_CANCEL"))
        assertTrue(service.contains("NotificationManager.IMPORTANCE_LOW"))
    }

    @Test
    fun notificationActionsUseExplicitImmutableServicePendingIntents() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadForegroundService.kt").readText()
        assertTrue(service.contains("Intent(this, DownloadForegroundService::class.java).setAction(action)"))
        assertTrue(service.contains("PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT"))
    }
}
