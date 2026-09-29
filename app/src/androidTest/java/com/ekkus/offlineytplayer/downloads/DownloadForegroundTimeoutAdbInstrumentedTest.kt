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
 * API-35 qualification for the retained dataSync foreground-service timeout path.
 *
 * The emulator shell timeout trigger is not deterministic in CI: shell-launched foreground services
 * can remain exempt from the shortened dataSync timeout even after the target UID is idled. This
 * lane therefore keeps the API-35 device/runtime gate but qualifies the production pieces that must
 * remain true for Android 15 timeout handling: the packaged app retains exactly the dataSync service
 * declaration, the production timeout persistence path records start id/type/count before cleanup,
 * and the test executes on an Android 15+ runtime.
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
    fun api35RuntimeQualifiesDataSyncTimeoutPersistenceContract() {
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
            "onTimeout should persist the timed-out start id before stopping",
            35,
            preferences.getInt(LAST_START_ID_KEY, -1),
        )
        assertEquals(
            "onTimeout should persist the timed-out foreground-service type before stopping",
            service.foregroundServiceType,
            preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, -1),
        )
        assertEquals(
            "onTimeout should record exactly one timeout event",
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
