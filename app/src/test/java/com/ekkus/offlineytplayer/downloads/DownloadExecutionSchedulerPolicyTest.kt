package com.ekkus.offlineytplayer.downloads

import android.app.job.JobInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadExecutionSchedulerPolicyTest {
    @Test
    fun schedulerSelectsUserInitiatedJobsOnlyOnApi34AndNewer() {
        assertEquals(
            DownloadSchedulerKind.ForegroundServiceFallback,
            DownloadExecutionSchedulerPolicy.schedulerKindForSdk(33),
        )
        assertEquals(
            DownloadSchedulerKind.UserInitiatedDataTransferJob,
            DownloadExecutionSchedulerPolicy.schedulerKindForSdk(34),
        )
        assertEquals(
            DownloadSchedulerKind.UserInitiatedDataTransferJob,
            DownloadExecutionSchedulerPolicy.schedulerKindForSdk(36),
        )
    }

    @Test
    fun networkPreferenceMapsToJobConstraints() {
        assertEquals(
            JobInfo.NETWORK_TYPE_ANY,
            DownloadExecutionSchedulerPolicy.requiredNetworkType(DownloadNetworkPreference.AnyNetwork),
        )
        assertEquals(
            JobInfo.NETWORK_TYPE_UNMETERED,
            DownloadExecutionSchedulerPolicy.requiredNetworkType(DownloadNetworkPreference.WifiOnly),
        )
    }

    @Test
    fun userInitiatedPathHasManifestPermissionAndJobService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.RUN_USER_INITIATED_JOBS"))
        assertTrue(manifest.contains(".downloads.DownloadUserInitiatedJobService"))
        assertTrue(manifest.contains("android.permission.BIND_JOB_SERVICE"))
    }

    @Test
    fun schedulingModelUsesOneDurableQueueContract() {
        assertTrue(DownloadExecutionSchedulerPolicy.UsesSharedDurableQueue)
        assertTrue(DownloadExecutionSchedulerPolicy.UidtUpdatesNotification)
    }
}
