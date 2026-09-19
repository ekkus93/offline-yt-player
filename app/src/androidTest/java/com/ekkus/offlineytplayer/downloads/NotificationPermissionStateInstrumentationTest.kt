package com.ekkus.offlineytplayer.downloads

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermissionStateInstrumentationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun clearState() {
        DownloadNotificationPermissionStateStore.clearRecordedGrantState(context)
    }

    @Test
    fun grantedAndDeniedOutcomesArePersistedWithoutInventingAnInitialState() {
        DownloadNotificationPermissionStateStore.clearRecordedGrantState(context)
        assertNull(DownloadNotificationPermissionStateStore.recordedGrantState(context))

        DownloadNotificationPermissionStateStore.recordGrantState(context, true)
        assertEquals(true, DownloadNotificationPermissionStateStore.recordedGrantState(context))

        DownloadNotificationPermissionStateStore.recordGrantState(context, false)
        assertEquals(false, DownloadNotificationPermissionStateStore.recordedGrantState(context))
    }
}
