package com.ekkus.offlineytplayer.downloads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadForegroundTimeoutInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @After
    fun clearState() {
        preferences.edit().clear().commit()
    }

    @Test
    fun packagedAppPersistsTimeoutStateBeforeServiceStopPathCanRelyOnIt() {
        preferences.edit().clear().commit()
        assertFalse(preferences.contains(LAST_START_ID_KEY))

        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 41,
            foregroundServiceType = 8,
        )

        assertEquals(41, preferences.getInt(LAST_START_ID_KEY, -1))
        assertEquals(8, preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, -1))
        assertEquals(1, preferences.getInt(TIMEOUT_COUNT_KEY, 0))
    }

    @Test
    fun packagedAppIncrementsTimeoutCountAcrossRepeatedTimeouts() {
        preferences.edit().clear().commit()

        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 11,
            foregroundServiceType = 1,
        )
        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 12,
            foregroundServiceType = 2,
        )

        assertEquals(12, preferences.getInt(LAST_START_ID_KEY, -1))
        assertEquals(2, preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, -1))
        assertEquals(2, preferences.getInt(TIMEOUT_COUNT_KEY, 0))
    }

    private companion object {
        const val PREFERENCES_NAME = "download_foreground_timeout"
        const val LAST_START_ID_KEY = "last_start_id"
        const val LAST_FOREGROUND_SERVICE_TYPE_KEY = "last_foreground_service_type"
        const val TIMEOUT_COUNT_KEY = "timeout_count"
    }
}
