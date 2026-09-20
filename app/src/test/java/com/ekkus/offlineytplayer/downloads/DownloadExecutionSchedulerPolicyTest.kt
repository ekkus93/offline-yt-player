package com.ekkus.offlineytplayer.downloads

import android.app.job.JobInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun schedulerSdkGatesEstimatedBytesAndUserInitiatedFlags() {
        assertFalse(DownloadExecutionSchedulerPolicy.supportsEstimatedNetworkBytes(27))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsEstimatedNetworkBytes(28))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsEstimatedNetworkBytes(34))

        assertFalse(DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(33))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(34))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(36))
    }

    @Test
    fun api26Through33UseForegroundFallbackInsteadOfDuplicatedSchedulerState() {
        assertTrue(DownloadExecutionSchedulerPolicy.usesForegroundFallback(26))
        assertTrue(DownloadExecutionSchedulerPolicy.usesForegroundFallback(33))
        assertFalse(DownloadExecutionSchedulerPolicy.usesForegroundFallback(34))
        assertTrue(DownloadServicePolicy.ReconcilesDurableQueueOnStart)
        assertTrue(DownloadExecutionSchedulerPolicy.UsesSharedDurableQueue)
        assertEquals(DownloadForegroundService.ACTION_SCHEDULE_WORK, "com.ekkus.offlineytplayer.download.SCHEDULE_WORK")
        assertEquals(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, DownloadUserInitiatedJobService.ExtraQueueItemId)
    }

    @Test
    fun uidtJobCarriesOnlyDurableQueueIdentityInExtras() {
        val extras = DownloadExecutionSchedulerPolicy.durableQueueItemExtras("queue-42")

        assertEquals("queue-42", extras.getString(DownloadUserInitiatedJobService.ExtraQueueItemId))
        assertNull(extras.getString("url"))
        assertNull(extras.getString("title"))
        assertNull(extras.getString("provider_payload"))
    }

    @Test
    fun stableJobIdsAreDeterministicAndInsideConfiguredRange() {
        val first = DownloadExecutionSchedulerPolicy.stableJobId("queue-item-1")
        val second = DownloadExecutionSchedulerPolicy.stableJobId("queue-item-1")

        assertEquals(first, second)
        assertTrue(first >= DownloadUserInitiatedJobService.JobIdBase)
        assertTrue(first < DownloadUserInitiatedJobService.JobIdBase + DownloadUserInitiatedJobService.JobIdRange)
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
