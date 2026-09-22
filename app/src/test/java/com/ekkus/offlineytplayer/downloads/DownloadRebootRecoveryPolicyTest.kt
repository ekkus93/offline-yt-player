package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRebootRecoveryPolicyTest {
    @Test
    fun manifestDeclaresPrivateUnlockedBootRecoveryReceiverOnly() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.contains("android:name=\".downloads.DownloadRebootReceiver\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertFalse(manifest.contains("android.intent.action.LOCKED_BOOT_COMPLETED"))
    }

    @Test
    fun rebootReceiverDoesNotStartForegroundServiceOrTouchProtectedState() {
        val receiver = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadRebootReceiver.kt").readText()
        assertFalse(receiver.contains("ContextCompat.startForegroundService"))
        assertFalse(receiver.contains("DownloadForegroundService::class.java"))
        assertTrue(receiver.contains("DownloadBootRecovery.onReceive(intent?.action)"))
        assertFalse(receiver.contains("AndroidDownloadExecutionScheduler"))
        assertFalse(receiver.contains("startService("))
        assertFalse(DownloadBootRecovery.StartsForegroundServiceFromBoot)
        assertFalse(DownloadBootRecovery.UsesLockedBootCompleted)
        assertFalse(DownloadBootRecovery.OpensCredentialProtectedStorageOnBoot)
    }

    @Test
    fun servicePolicyKeepsDurableRecoveryRequirementForSchedulerWiring() {
        assertTrue(DownloadServicePolicy.SupportsBootRecovery)
        assertTrue(DownloadServicePolicy.ReconcilesDurableQueueOnStart)
        assertTrue(DownloadServicePolicy.FailsInterruptedTransfersExplicitly)
    }
}
