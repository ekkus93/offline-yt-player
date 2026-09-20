package com.ekkus.offlineytplayer.downloads

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRebootReceiverInstrumentedTest {
    @Test
    fun bootCompletedDoesNotStartForegroundOrBackgroundService() {
        val context = RejectServiceStartContext(InstrumentationRegistry.getInstrumentation().targetContext)

        DownloadRebootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun lockedBootCompletedIsIgnored() {
        val context = RejectServiceStartContext(InstrumentationRegistry.getInstrumentation().targetContext)

        DownloadRebootReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
    }

    private class RejectServiceStartContext(base: Context) : ContextWrapper(base) {
        override fun startService(service: Intent?): ComponentName? =
            error("boot receiver must not start a background service")

        override fun startForegroundService(service: Intent?): ComponentName? =
            error("boot receiver must not start a foreground service")
    }
}
