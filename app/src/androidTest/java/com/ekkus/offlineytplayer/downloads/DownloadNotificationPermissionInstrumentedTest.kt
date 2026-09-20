package com.ekkus.offlineytplayer.downloads

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadNotificationPermissionInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun clearState() {
        DownloadNotificationPermissionStateStore.clearRecordedGrantState(context)
    }

    @Test
    fun packagedAppRecordsGrantedNotificationPermissionState() {
        DownloadNotificationPermissionStateStore.clearRecordedGrantState(context)

        DownloadNotificationPermissionStateStore.recordGrantState(context, granted = true)

        assertEquals(true, DownloadNotificationPermissionStateStore.recordedGrantState(context))
    }

    @Test
    fun packagedAppRecordsDeniedNotificationPermissionStateWithoutQueueMutation() {
        DownloadNotificationPermissionStateStore.clearRecordedGrantState(context)

        DownloadNotificationPermissionStateStore.recordGrantState(context, granted = false)

        assertEquals(false, DownloadNotificationPermissionStateStore.recordedGrantState(context))
        assertEquals(
            DownloadNotificationPermissionBehavior.QueueStateOnly,
            DownloadNotificationPermissionPolicy.behavior(granted = false),
        )
        assertEquals(true, DownloadNotificationPermissionPolicy.DenialDoesNotMutateDurableQueue)
    }

    @Test
    fun packagedAppClearsRecordedNotificationPermissionState() {
        DownloadNotificationPermissionStateStore.recordGrantState(context, granted = true)

        DownloadNotificationPermissionStateStore.clearRecordedGrantState(context)

        assertNull(DownloadNotificationPermissionStateStore.recordedGrantState(context))
    }
}
