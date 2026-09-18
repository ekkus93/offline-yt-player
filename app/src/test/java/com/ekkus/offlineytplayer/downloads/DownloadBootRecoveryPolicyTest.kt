package com.ekkus.offlineytplayer.downloads

import org.junit.Assert.assertFalse
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
}
