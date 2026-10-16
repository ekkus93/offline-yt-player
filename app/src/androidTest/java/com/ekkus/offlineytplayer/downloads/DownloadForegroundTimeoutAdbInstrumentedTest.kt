package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API-35 contract check for the timeout persistence store and retained service declaration.
 *
 * This test does not start DownloadForegroundService or invoke Service.onTimeout. The actual
 * shortened-timeout callback remains an open qualification item in the remediation TODO.
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

    private fun clearTimeoutState() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "download_foreground_timeout"
        const val LAST_START_ID_KEY = "last_start_id"
        const val LAST_FOREGROUND_SERVICE_TYPE_KEY = "last_foreground_service_type"
        const val TIMEOUT_COUNT_KEY = "timeout_count"
    }
}
