package com.ekkus.offlineytplayer.downloads

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRebootReceiverInstrumentedTest {
    @Test
    fun bootCompletedReceiverPathIsSafeToInvokeInPackagedApp() {
        val receiver = DownloadRebootReceiver()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun nonBootIntentIsIgnoredByReceiverPath() {
        val receiver = DownloadRebootReceiver()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        receiver.onReceive(context, Intent(Intent.ACTION_PACKAGE_REPLACED))
    }
}
