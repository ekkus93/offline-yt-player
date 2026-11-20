package com.ekkus.offlineytplayer.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import java.io.File
import org.junit.Assert.assertTrue
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


    @Test
    fun platformNotificationPermissionPhaseKeepsNativeDurableQueue() {
        if (Build.VERSION.SDK_INT < 33) return
        val phase = InstrumentationRegistry.getArguments()
            .getString("expectedNotificationPermission")
        val permissionGranted =
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (phase != null) {
            assertEquals(
                "host-configured notification permission phase was lost before instrumentation",
                phase == "granted",
                permissionGranted,
            )
        }
        val database = File(context.filesDir, "cov403-notification-permission.sqlite3")
        val jobId = "cov403-durable-notification"
        // Denied and granted phases open the same on-disk queue in isolated
        // instrumentation processes; a permission change must not discard it.
        System.loadLibrary("offline_yt_core")
        if (phase == "denied") {
            // Start from an isolated fixture. The next phase must see this
            // same durable row, not silently create another after a reset.
            listOf("", "-wal", "-shm").forEach { suffix ->
                File(database.absolutePath + suffix).delete()
            }
        }
        if (phase == "granted") {
            GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val oldQueue = core.listDownloadQueue()
                assertEquals(null, oldQueue.error)
                assertEquals(
                    "previous denied phase must leave a queued job on disk",
                    jobId,
                    requireNotNull(oldQueue.value).single().jobId,
                )
            }
        }
        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { gateway ->
            val result = gateway.enqueue(jobId)
            assertEquals(null, result.error)
            if (phase != null) {
                assertEquals("only the first permission phase may enqueue", phase == "denied", result.value)
            }
        }
        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val queue = core.listDownloadQueue()
            assertEquals(null, queue.error)
            val snapshots = requireNotNull(queue.value)
            assertEquals(1, snapshots.size)
            assertEquals(jobId, snapshots.single().jobId)
            assertEquals(CoreDownloadState.QUEUED, snapshots.single().state)
        }
        DownloadNotificationPermissionStateStore.recordGrantState(context, permissionGranted)
        assertEquals(
            permissionGranted,
            DownloadNotificationPermissionStateStore.recordedGrantState(context),
        )
        if (phase == "granted") {
            assertTrue(database.exists())
            listOf("", "-wal", "-shm").forEach { suffix ->
                File(database.absolutePath + suffix).delete()
            }
        }
    }
}
