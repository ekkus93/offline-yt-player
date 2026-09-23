package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API-35 qualification for Android's dataSync foreground-service timeout path.
 *
 * The regular API-29 smoke lane proves packaged persistence behavior. This test
 * is intentionally isolated in the API-35 foreground-timeout workflow because it
 * mutates device_config and waits for the platform timeout callback.
 */
@RunWith(AndroidJUnit4::class)
class DownloadForegroundTimeoutAdbInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun configureShortDataSyncTimeout() {
        assumeTrue("Android 15+ is required for Service.onTimeout foreground-service qualification", Build.VERSION.SDK_INT >= 35)
        clearTimeoutState()
        shell("device_config put activity_manager data_sync_fgs_timeout_duration 1000")
    }

    @After
    fun restoreDeviceConfigAndStopService() {
        if (Build.VERSION.SDK_INT >= 35) {
            shell("device_config delete activity_manager data_sync_fgs_timeout_duration")
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
        clearTimeoutState()
    }

    @Test
    fun shortenedDataSyncTimeoutInvokesOnTimeoutAndPersistsBeforeStop() {
        val intent = Intent(context, DownloadForegroundService::class.java)
            .setAction(DownloadForegroundService.ACTION_SCHEDULE_WORK)
            .putExtra(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, "api35-timeout-qualification")

        context.startForegroundService(intent)
        shell("input keyevent KEYCODE_HOME")

        val deadline = SystemClock.uptimeMillis() + TIMEOUT_WAIT_MILLIS
        while (SystemClock.uptimeMillis() < deadline && preferences.getInt(TIMEOUT_COUNT_KEY, 0) == 0) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }

        assertTrue(
            "DownloadForegroundService.onTimeout should persist timeout state after shortened dataSync timeout",
            preferences.getInt(TIMEOUT_COUNT_KEY, 0) > 0,
        )
        assertTrue(
            "onTimeout should persist the timed-out start id before stopping",
            preferences.getInt(LAST_START_ID_KEY, -1) > 0,
        )
        assertTrue(
            "onTimeout should persist the timed-out foreground-service type before stopping",
            preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, -1) > 0,
        )
    }

    private fun clearTimeoutState() {
        preferences.edit().clear().commit()
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader -> reader.readText() }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "download_foreground_timeout"
        const val LAST_START_ID_KEY = "last_start_id"
        const val LAST_FOREGROUND_SERVICE_TYPE_KEY = "last_foreground_service_type"
        const val TIMEOUT_COUNT_KEY = "timeout_count"
        const val TIMEOUT_WAIT_MILLIS = 60_000L
        const val POLL_INTERVAL_MILLIS = 1_000L
    }
}
