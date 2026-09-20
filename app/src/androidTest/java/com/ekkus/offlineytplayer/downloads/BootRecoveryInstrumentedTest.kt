package com.ekkus.offlineytplayer.downloads

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootRecoveryInstrumentedTest {
    @Test
    fun packagedReceiverHandlesBootButNotLockedBoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager

        val bootReceivers = packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName),
            0,
        )
        assertTrue(
            "DownloadRebootReceiver must be registered for BOOT_COMPLETED",
            bootReceivers.any { it.activityInfo.name == DownloadRebootReceiver::class.java.name },
        )

        val lockedBootReceivers = packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).setPackage(context.packageName),
            0,
        )
        assertFalse(
            "Credential-protected download state must not be touched from LOCKED_BOOT_COMPLETED",
            lockedBootReceivers.any { it.activityInfo.name == DownloadRebootReceiver::class.java.name },
        )
    }

    @Test
    fun bootCallbackDoesNotStartForegroundServiceOrTouchCredentialState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DownloadRebootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertFalse(DownloadBootRecovery.StartsForegroundServiceFromBoot)
        assertFalse(DownloadBootRecovery.UsesLockedBootCompleted)
    }
}
