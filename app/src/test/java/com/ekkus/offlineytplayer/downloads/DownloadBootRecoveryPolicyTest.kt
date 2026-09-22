package com.ekkus.offlineytplayer.downloads

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBootRecoveryPolicyTest {
    @Test
    fun bootCompletedIsTheOnlyAcceptedRecoverySignal() {
        assertTrue(DownloadBootRecovery.shouldReconcile(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(DownloadBootRecovery.shouldReconcile(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertFalse(DownloadBootRecovery.shouldReconcile(null))
    }

    @Test
    fun bootRecoveryNeverStartsOrSchedulesTransferExecution() {
        assertFalse(DownloadBootRecovery.StartsForegroundServiceFromBoot)
        assertFalse(DownloadBootRecovery.SchedulesUserInitiatedJobFromBoot)
        assertFalse(DownloadBootRecovery.UsesLockedBootCompleted)
    }
}
