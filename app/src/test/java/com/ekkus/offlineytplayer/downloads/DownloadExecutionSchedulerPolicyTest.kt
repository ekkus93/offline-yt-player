package com.ekkus.offlineytplayer.downloads

import java.io.File
import android.app.job.JobParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals(DownloadForegroundService.ACTION_SCHEDULE_WORK, "com.ekkus.offlineytplayer.download.SCHEDULE_WORK")
        assertEquals(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, DownloadUserInitiatedJobService.ExtraQueueItemId)
    }

    @Test
    fun uidtJobCarriesOnlyDurableQueueIdentityInExtras() {
        val scheduler = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt").readText()
        val extrasFunction = scheduler.substringAfter("fun durableQueueItemExtras").substringBefore("fun stableJobId")

        assertTrue(extrasFunction.contains("putString(DownloadUserInitiatedJobService.ExtraQueueItemId, queueItemId)"))
        assertFalse(extrasFunction.contains("url"))
        assertFalse(extrasFunction.contains("title"))
        assertFalse(extrasFunction.contains("provider_payload"))
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
    fun minimumIntHashStillProducesJobIdInsideReservedRange() {
        // Java String.hashCode() for this input is Int.MIN_VALUE; Int.absoluteValue overflows.
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        val jobId = DownloadExecutionSchedulerPolicy.stableJobId("polygenelubricants")
        assertTrue(jobId >= DownloadUserInitiatedJobService.JobIdBase)
        assertTrue(jobId < DownloadUserInitiatedJobService.JobIdBase + DownloadUserInitiatedJobService.JobIdRange)
    }

    @Test
    fun hashCollisionNeverReplacesAnotherDurableTransfer() {
        // Java hashes collide, so the legacy direct hash ID would replace Aa
        // when BB was scheduled.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val firstId = DownloadExecutionSchedulerPolicy.stableJobId("Aa")
        assertEquals(firstId, DownloadExecutionSchedulerPolicy.stableJobId("BB"))
        val occupied = mapOf(firstId to "Aa")
        assertEquals(firstId, DownloadExecutionSchedulerPolicy.availableJobId("Aa", occupied))
        val secondId = DownloadExecutionSchedulerPolicy.availableJobId("BB", occupied)
        assertNotEquals(firstId, secondId)
        assertEquals(
            secondId,
            DownloadExecutionSchedulerPolicy.availableJobId("BB", occupied + (secondId!! to "BB")),
        )
        assertNotEquals(firstId, DownloadExecutionSchedulerPolicy.availableJobId("Aa", mapOf(firstId to null)))
    }

    @Test
    fun reschedulingCollisionVictimReusesItsExistingJobAfterEarlierSlotBecomesFree() {
        val firstId = DownloadExecutionSchedulerPolicy.stableJobId("Aa")
        val secondId = DownloadExecutionSchedulerPolicy.availableJobId("BB", mapOf(firstId to "Aa"))!!
        // The first collision occupant completed, leaving its slot available.
        // A reschedule of BB must reuse BB's pending job, not create a duplicate.
        assertEquals(secondId, DownloadExecutionSchedulerPolicy.availableJobId("BB", mapOf(secondId to "BB")))
    }

    @Test
    fun invalidJobExtrasReturnAlreadyFinishedWithoutDoubleSettlement() {
        // Android treats onStartJob=false as an already-completed job. Ensure
        // malformed durable IDs cannot double-settle or post a UIDT notification.
        val source = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt").readText()
        val onStart = source.substringAfter("override fun onStartJob(params: JobParameters): Boolean {")
            .substringBefore("Thread {")
        val invalidIdBranch = onStart.substringAfter("if (queueItemId.isNullOrBlank()) {")
            .substringBefore("}")
        assertTrue(invalidIdBranch.contains("return false"))
        assertFalse(invalidIdBranch.contains("jobFinished("))
        assertTrue(onStart.indexOf("if (queueItemId.isNullOrBlank())") < onStart.indexOf("setNotification("))
    }

    @Test
    fun jobIsSettledExactlyOnceWhenWorkerThrowsUnexpectedly() {
        var settlements = 0
        var executed = 0
        try {
            runAndSettleDownloadJob(
                work = { executed++; throw IllegalStateException("unexpected worker failure") },
                settle = { settlements++ },
            )
            org.junit.Assert.fail("Expected the unexpected worker failure to propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("unexpected worker failure", expected.message)
        }
        assertEquals(1, executed)
        assertEquals(1, settlements)
        runAndSettleDownloadJob(work = { executed++ }, settle = { settlements++ })
        assertEquals(2, executed)
        assertEquals(2, settlements)
    }

    @Test
    fun platformStopSignalsNativeOnceAndSuppressesLateJobSettlement() {
        var stopSignals = 0
        var settlements = 0
        val attempt = UidtJobAttempt { stopSignals++ }

        assertTrue(attempt.stop())
        assertFalse(attempt.stop())
        assertFalse(attempt.finishIfRunning { settlements++ })
        attempt.markWorkerExited()
        assertEquals(1, stopSignals)
        assertEquals(0, settlements)
    }

    @Test
    fun completedAttemptCannotBeStoppedOrSettledAgain() {
        var stopSignals = 0
        var settlements = 0
        val attempt = UidtJobAttempt { stopSignals++ }

        assertTrue(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.stop())
        assertEquals(1, settlements)
        assertEquals(0, stopSignals)
    }

    @Test
    fun replacementWaitsUntilPreviousNativeWriterHasExited() {
        val previous = UidtJobAttempt()
        val replacement = UidtJobAttempt()
        val entered = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        val waiter = Thread {
            entered.countDown()
            result.set(replacement.awaitPredecessor(previous))
            completed.countDown()
        }
        waiter.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS))
            previous.markWorkerExited()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(true, result.get())
        } finally {
            previous.markWorkerExited()
            waiter.join(2_000)
        }
    }

    @Test
    fun stoppedReplacementDoesNotBeginBeforePreviousNativeWriterExits() {
        val previous = UidtJobAttempt()
        val replacement = UidtJobAttempt()
        val entered = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        val waiter = Thread {
            entered.countDown()
            result.set(replacement.awaitPredecessor(previous))
            completed.countDown()
        }
        waiter.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS))
            assertTrue(replacement.stop())
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(false, result.get())
        } finally {
            previous.markWorkerExited()
            waiter.join(2_000)
        }
    }

    @Test
    fun userAndAppRequestedUidtStopsDoNotAutomaticallyReschedule() {
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_USER))
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_CANCELLED_BY_APP))
        assertTrue(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_TIMEOUT))
    }

    @Test
    fun exhaustedJobIdRangeFailsClosedInsteadOfReplacingAnotherJob() {
        val occupied = (DownloadUserInitiatedJobService.JobIdBase until
            DownloadUserInitiatedJobService.JobIdBase + DownloadUserInitiatedJobService.JobIdRange)
            .associateWith { "another-queue-item" }
        assertNull(DownloadExecutionSchedulerPolicy.availableJobId("new-queue-item", occupied))
    }

    @Test
    fun userInitiatedPathHasManifestPermissionAndJobService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.RUN_USER_INITIATED_JOBS"))
        assertTrue(manifest.contains(".downloads.DownloadUserInitiatedJobService"))
        assertTrue(manifest.contains("android.permission.BIND_JOB_SERVICE"))
    }

}
