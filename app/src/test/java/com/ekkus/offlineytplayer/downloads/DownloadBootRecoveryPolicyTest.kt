package com.ekkus.offlineytplayer.downloads

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBootRecoveryPolicyTest {
    @Test
    fun bootRecoveryNeverStartsDataSyncForegroundService() {
        assertFalse(DownloadBootRecovery.StartsForegroundServiceFromBoot)
    }

    @Test
    fun lockedBootIsNotPartOfV1RecoveryContract() {
        assertFalse(DownloadBootRecovery.UsesLockedBootCompleted)
    }

    @Test
    fun bootCompletedDefersToStartupReconciliationWithoutOpeningProtectedState() {
        val decision = DownloadBootRecovery.onReceive(Intent.ACTION_BOOT_COMPLETED)

        assertEquals(DownloadBootRecoveryDisposition.DeferUntilAppStartup, decision.disposition)
        assertTrue(decision.receiverHandled)
        assertFalse(decision.startsForegroundService)
        assertFalse(decision.opensCredentialProtectedStorage)
        assertTrue(decision.workRemainsRecoverable)
        assertTrue(decision.scheduledOnlyWhenLegal)
        assertTrue(decision.reconciliationPath!!.contains("reconcileStartup"))
    }

    @Test
    fun nonBootBroadcastsAreIgnoredAndCannotScheduleWork() {
        val decision = DownloadBootRecovery.onReceive(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertEquals(DownloadBootRecoveryDisposition.Ignore, decision.disposition)
        assertFalse(decision.receiverHandled)
        assertFalse(decision.startsForegroundService)
        assertFalse(decision.opensCredentialProtectedStorage)
        assertFalse(decision.workRemainsRecoverable)
        assertTrue(decision.scheduledOnlyWhenLegal)
        assertNull(decision.reconciliationPath)
    }
}
