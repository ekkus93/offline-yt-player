package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRebootRecoveryPolicyTest {
    @Test
    fun manifestDeclaresPrivateBootRecoveryReceiver() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.contains("android:name=\".downloads.DownloadRebootReceiver\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.intent.action.LOCKED_BOOT_COMPLETED"))
    }

    @Test
    fun rebootReceiverStartsServiceThroughExplicitRecoveryAction() {
        val receiver = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadRebootReceiver.kt").readText()
        assertTrue(receiver.contains("ContextCompat.startForegroundService"))
        assertTrue(receiver.contains("DownloadForegroundService::class.java"))
        assertTrue(receiver.contains("ACTION_RECONCILE_AFTER_REBOOT"))
    }

    @Test
    fun servicePolicyRequiresDurableQueueReconciliationAndExplicitFailure() {
        assertTrue(DownloadServicePolicy.SupportsBootRecovery)
        assertTrue(DownloadServicePolicy.ReconcilesDurableQueueOnStart)
        assertTrue(DownloadServicePolicy.FailsInterruptedTransfersExplicitly)
    }
}
