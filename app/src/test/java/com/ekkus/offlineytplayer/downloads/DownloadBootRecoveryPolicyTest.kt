package com.ekkus.offlineytplayer.downloads

import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadBootRecoveryPolicyTest {
    @Test
    fun bootRecoveryNeverStartsForegroundDataSyncService() {
        assertFalse(DownloadBootRecovery.StartsForegroundServiceFromBoot)
    }

    @Test
    fun lockedBootIsNotRegisteredForCredentialProtectedState() {
        assertFalse(DownloadBootRecovery.UsesLockedBootCompleted)
    }
}
