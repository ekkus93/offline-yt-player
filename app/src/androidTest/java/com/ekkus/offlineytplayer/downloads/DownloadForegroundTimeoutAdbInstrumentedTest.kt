package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API-35 tests for the timeout persistence store and a real shortened platform timeout callback.
 */
@RunWith(AndroidJUnit4::class)
class DownloadForegroundTimeoutAdbInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun requireAndroid15AndClearState() {
        assumeTrue("Android 15+ is required for Service.onTimeout foreground-service qualification", Build.VERSION.SDK_INT >= 35)
        clearTimeoutState()
    }

    @After
    fun clearState() {
        context.stopService(android.content.Intent(context, DownloadForegroundService::class.java))
        DownloadForegroundService.serviceDestroyedObserverForTesting = null
        clearTimeoutState()
    }

    @Test
    fun api35RuntimeChecksTimeoutStorePersistenceContract() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES,
        )
        val service = packageInfo.services.orEmpty().single { service ->
            service.name == DownloadForegroundServiceInventory.RetainedDataSyncService
        }

        assertTrue(
            "DownloadForegroundService must retain dataSync foreground-service type on API 35",
            service.foregroundServiceType > 0,
        )

        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 35,
            foregroundServiceType = service.foregroundServiceType,
        )

        assertEquals(
            "timeout store should persist the start id synchronously",
            35,
            preferences.getInt(LAST_START_ID_KEY, -1),
        )
        assertEquals(
            "timeout store should persist the foreground-service type synchronously",
            service.foregroundServiceType,
            preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, -1),
        )
        assertEquals(
            "timeout store should record one timeout event",
            1,
            preferences.getInt(TIMEOUT_COUNT_KEY, 0),
        )
    }

    @Test
    fun api35ShortenedTimeoutInvokesProductionServiceCallbackAndStopsCleanly() {
        val serviceStopped = CountDownLatch(1)
        DownloadForegroundService.serviceDestroyedObserverForTesting = { serviceStopped.countDown() }
        val serviceIntent = android.content.Intent(context, DownloadForegroundService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)

        // Let the service enter the foreground before moving the app to the background, where
        // Android applies the dataSync time limit. The CI driver sets that limit to 10 seconds.
        SystemClock.sleep(1_000L)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 3")
            .close()

        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_WAIT_MS
        while (
            preferences.getInt(TIMEOUT_COUNT_KEY, 0) == 0 &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            SystemClock.sleep(100L)
        }

        assertEquals(
            "Android's shortened timeout must enter DownloadForegroundService.onTimeout",
            1,
            preferences.getInt(TIMEOUT_COUNT_KEY, 0),
        )
        assertTrue(
            "the production callback must persist the actual service start id",
            preferences.getInt(LAST_START_ID_KEY, 0) > 0,
        )
        assertEquals(
            "the callback must report the retained dataSync service type",
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, 0),
        )

        assertTrue(
            "DownloadForegroundService must stop after its production timeout callback",
            serviceStopped.await(STOP_WAIT_MS, TimeUnit.MILLISECONDS),
        )
    }

    private fun clearTimeoutState() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "download_foreground_timeout"
        const val LAST_START_ID_KEY = "last_start_id"
        const val LAST_FOREGROUND_SERVICE_TYPE_KEY = "last_foreground_service_type"
        const val TIMEOUT_COUNT_KEY = "timeout_count"
        const val TIMEOUT_WAIT_MS = 60_000L
        const val STOP_WAIT_MS = 10_000L
    }
}
